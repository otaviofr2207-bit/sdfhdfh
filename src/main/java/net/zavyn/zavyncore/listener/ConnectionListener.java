package net.zavyn.zavyncore.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.config.PluginConfig;
import net.zavyn.zavyncore.database.dao.PlayerDao;
import net.zavyn.zavyncore.integration.FloodgateIntegration;
import net.zavyn.zavyncore.model.AccountType;
import net.zavyn.zavyncore.model.Punishment;
import net.zavyn.zavyncore.service.AccountLimitService;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.service.ConfirmationService;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuida de todo o ciclo de conexao: deteccao de tipo de conta, checagem de ban (conta e IP),
 * limite de contas por IP e disparo do fluxo de confirmacao (novo IP / periodica).
 */
public final class ConnectionListener {

    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginConfig config;
    private final Messages messages;
    private final PlayerDao playerDao;
    private final PunishmentService punishmentService;
    private final AccountLimitService accountLimitService;
    private final AuthService authService;
    private final ConfirmationService confirmationService;
    private final FloodgateIntegration floodgate;

    // Preenchido em GameProfileRequestEvent (onde sabemos se a conexao e online-mode) e
    // consumido em LoginEvent. Entradas orfas sao removidas em DisconnectEvent por seguranca.
    private final Map<UUID, Boolean> onlineModeCache = new ConcurrentHashMap<>();

    public ConnectionListener(ProxyServer proxy, Logger logger, PluginConfig config, Messages messages,
                               PlayerDao playerDao, PunishmentService punishmentService,
                               AccountLimitService accountLimitService, AuthService authService,
                               ConfirmationService confirmationService, FloodgateIntegration floodgate) {
        this.proxy = proxy;
        this.logger = logger;
        this.config = config;
        this.messages = messages;
        this.playerDao = playerDao;
        this.punishmentService = punishmentService;
        this.accountLimitService = accountLimitService;
        this.authService = authService;
        this.confirmationService = confirmationService;
        this.floodgate = floodgate;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        onlineModeCache.put(event.getGameProfile().getId(), event.isOnlineMode());
    }

    @Subscribe(order = PostOrder.LATE)
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getUsername();
        String ip = extractIp(player);

        AccountType accountType = resolveAccountType(uuid);
        onlineModeCache.remove(uuid);
        boolean bypassLimit = player.hasPermission("zavyncore.accountlimit.bypass");

        // Toda a cadeia roda fora da thread de eventos do Velocity; o resultado final so e aplicado
        // quando o CompletableFuture completa (EventTask.resumeWhenComplete evita bloquear o proxy).
        var future = punishmentService.checkBan(uuid).thenCompose(banPunishment -> {
            if (banPunishment != null) {
                event.setResult(ResultedEvent.ComponentResult.denied(banMessage(banPunishment)));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            java.util.concurrent.CompletableFuture<Boolean> ipBanCheck = ip == null
                    ? java.util.concurrent.CompletableFuture.completedFuture(false)
                    : punishmentService.checkIpBanned(ip);

            return ipBanCheck.thenCompose(ipBanned -> {
                if (Boolean.TRUE.equals(ipBanned)) {
                    event.setResult(ResultedEvent.ComponentResult.denied(
                            Msg.parse(messages.get("ban.ip-banned"), Map.of("ip", ip))));
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }

                java.util.concurrent.CompletableFuture<AccountLimitService.LimitResult> limitCheck = ip == null
                        ? java.util.concurrent.CompletableFuture.completedFuture(AccountLimitService.LimitResult.permitted())
                        : accountLimitService.checkAndRegister(uuid, accountType, ip, bypassLimit);

                return limitCheck.thenAccept(limitResult -> {
                    if (!limitResult.allowed()) {
                        event.setResult(ResultedEvent.ComponentResult.denied(Msg.parse(
                                messages.get("account-limit.denied"),
                                Map.of("limit", String.valueOf(limitResult.limit())))));
                        return;
                    }

                    try {
                        playerDao.upsertOnConnect(uuid, name, accountType, ip);
                    } catch (Exception e) {
                        logger.error("[ZavynCore] Falha ao salvar jogador {} no banco: {}", name, e.getMessage());
                        event.setResult(ResultedEvent.ComponentResult.denied(Msg.parse(messages.get("errors.database"))));
                        return;
                    }

                    boolean autoLogin = (accountType == AccountType.PREMIUM
                            && config.getBoolean("authentication.premium-auto-login", true))
                            || (accountType == AccountType.FLOODGATE
                            && config.getBoolean("authentication.floodgate-auto-login", true));

                    if (autoLogin || accountType != AccountType.OFFLINE
                            || !config.getBoolean("authentication.offline-require-login", true)) {
                        authServiceMarkLoggedIn(uuid);
                    }
                    // Se for OFFLINE e exigir login: o estado "precisa logar" fica em AuthService
                    // (nao logado por padrao). A trava de acoes (mover, quebrar bloco, chat) ate o
                    // /login deve ser feita no servidor Lobby (Paper), que pode consultar esse estado
                    // via uma futura API de plugin messaging exposta pelo ZavynCore.
                });
            });
        });

        return EventTask.resumeWhenComplete(future);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        onlineModeCache.remove(uuid);
        authService.logout(uuid, event.getPlayer().getUsername());
    }

    private void authServiceMarkLoggedIn(UUID uuid) {
        // Login automatico nao passa pelo fluxo de senha; apenas marcamos sessao ativa.
        try {
            playerDao.setLoggedIn(uuid, true);
        } catch (Exception e) {
            logger.warn("[ZavynCore] Falha ao marcar sessao automatica de {}: {}", uuid, e.getMessage());
        }
    }

    private AccountType resolveAccountType(UUID uuid) {
        if (floodgate.isFloodgatePlayer(uuid)) {
            return AccountType.FLOODGATE;
        }
        boolean onlineMode = onlineModeCache.getOrDefault(uuid, proxy.getConfiguration().isOnlineMode());
        return onlineMode ? AccountType.PREMIUM : AccountType.OFFLINE;
    }

    private String extractIp(Player player) {
        InetSocketAddress address = player.getRemoteAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    private Component banMessage(Punishment punishment) {
        String template = punishment.isPermanent() ? messages.get("ban.permanent") : messages.get("ban.temporary");
        return Msg.parse(template, Map.of(
                "reason", punishment.reason(),
                "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                "punishment_id", punishment.punishmentId(),
                "staff", punishment.staffName()
        ));
    }
}
