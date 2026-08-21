package net.zavyn.zavyncore;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.command.*;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.config.PluginConfig;
import net.zavyn.zavyncore.database.Database;
import net.zavyn.zavyncore.database.dao.IpBanDao;
import net.zavyn.zavyncore.database.dao.LogDao;
import net.zavyn.zavyncore.database.dao.PlayerDao;
import net.zavyn.zavyncore.database.dao.PunishmentDao;
import net.zavyn.zavyncore.database.dao.WarnDao;
import net.zavyn.zavyncore.integration.FloodgateIntegration;
import net.zavyn.zavyncore.listener.ChatListener;
import net.zavyn.zavyncore.listener.ConnectionListener;
import net.zavyn.zavyncore.service.AccountLimitService;
import net.zavyn.zavyncore.service.AltsService;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.service.ConfirmationService;
import net.zavyn.zavyncore.service.MuteService;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.service.WarnService;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "zavyncore",
        name = "ZavynCore",
        version = ZavynCoreCommand.VERSION,
        description = "Sistema central da rede Zavyn: bans, mutes, warns, autenticacao e limite de contas.",
        authors = {"Zavyn"}
)
public final class ZavynCore {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private Messages messages;
    private Database database;
    private ExecutorService executor;
    private FloodgateIntegration floodgate;

    private PunishmentService punishmentService;
    private MuteService muteService;
    private WarnService warnService;
    private AuthService authService;
    private ConfirmationService confirmationService;
    private AccountLimitService accountLimitService;
    private AltsService altsService;

    @Inject
    public ZavynCore(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (!loadEverything()) {
            logger.error("[ZavynCore] Falha critica na inicializacao - o plugin ficara inativo. "
                    + "Verifique os erros acima (config, banco de dados, etc).");
            return;
        }

        registerListeners();
        registerCommands();

        logger.info("[ZavynCore] Habilitado com sucesso. Versao {} | Floodgate: {} | Online-mode do proxy: {}",
                ZavynCoreCommand.VERSION, floodgate.isPresent() ? "ativo" : "inativo", proxy.getConfiguration().isOnlineMode());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (database != null) {
            database.close();
        }
        logger.info("[ZavynCore] Desligado.");
    }

    private boolean loadEverything() {
        try {
            this.config = PluginConfig.load(dataDirectory);
            this.messages = Messages.load(dataDirectory);
        } catch (Exception e) {
            logger.error("[ZavynCore] Falha ao carregar config.yml/messages.yml: {}", e.getMessage());
            return false;
        }

        try {
            this.database = new Database(config, logger);
            this.database.migrate();
        } catch (Exception e) {
            logger.error("[ZavynCore] Falha ao conectar/migrar o banco de dados MySQL/MariaDB: {}", e.getMessage());
            logger.error("[ZavynCore] Verifique as credenciais em config.yml (secao 'database').");
            return false;
        }

        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "ZavynCore-Worker");
                    t.setDaemon(true);
                    return t;
                });

        this.floodgate = new FloodgateIntegration(logger);

        PlayerDao playerDao = new PlayerDao(database);
        PunishmentDao punishmentDao = new PunishmentDao(database);
        WarnDao warnDao = new WarnDao(database);
        IpBanDao ipBanDao = new IpBanDao(database);
        LogDao logDao = new LogDao(database);

        boolean banAlsoIp = config.getBoolean("punishment.ban-also-ip", false);

        this.punishmentService = new PunishmentService(punishmentDao, ipBanDao, logDao, executor, banAlsoIp);
        this.muteService = new MuteService(punishmentDao, logDao, executor);
        this.warnService = new WarnService(warnDao, logDao, executor, config, muteService, punishmentService);
        this.authService = new AuthService(playerDao, logDao, executor);
        this.confirmationService = new ConfirmationService(playerDao, config);
        this.accountLimitService = new AccountLimitService(database, playerDao, config, executor);
        this.altsService = new AltsService(playerDao, executor);

        return true;
    }

    private void registerListeners() {
        PlayerDao playerDao = new PlayerDao(database);
        proxy.getEventManager().register(this, new ConnectionListener(
                proxy, logger, config, messages, playerDao, punishmentService,
                accountLimitService, authService, confirmationService, floodgate));
        proxy.getEventManager().register(this, new ChatListener(messages, muteService, authService));
    }

    private void registerCommands() {
        CommandManager cm = proxy.getCommandManager();
        PlayerDao playerDao = new PlayerDao(database);
        TargetResolver resolver = new TargetResolver(proxy, playerDao, executor);

        register(cm, "ban", new BanCommand(proxy, resolver, punishmentService, messages));
        register(cm, "tempban", new TempBanCommand(proxy, resolver, punishmentService, messages));
        register(cm, "unban", new UnbanCommand(resolver, punishmentService, messages));
        register(cm, "banip", new BanIpCommand(proxy, punishmentService, messages));
        register(cm, "unbanip", new UnbanIpCommand(punishmentService, messages));
        register(cm, "checkban", new CheckBanCommand(resolver, punishmentService, messages));
        register(cm, "history", new HistoryCommand(resolver, punishmentService, messages));

        register(cm, "mute", new MuteCommand(resolver, muteService, messages));
        register(cm, "tempmute", new TempMuteCommand(resolver, muteService, messages));
        register(cm, "unmute", new UnmuteCommand(resolver, muteService, messages));
        register(cm, "checkmute", new CheckMuteCommand(resolver, muteService, messages));

        register(cm, "kick", new KickCommand(proxy, punishmentService, messages));

        register(cm, "warn", new WarnCommand(resolver, warnService, messages));
        register(cm, "warnings", new WarningsCommand(resolver, warnService, messages));
        register(cm, "clearwarnings", new ClearWarningsCommand(resolver, warnService, messages));

        register(cm, "register", new RegisterCommand(authService, messages));
        register(cm, "login", new LoginCommand(authService, confirmationService, messages));
        register(cm, "changepassword", new ChangePasswordCommand(authService, messages));
        register(cm, "unregister", new UnregisterCommand(authService, messages));
        register(cm, "logout", new LogoutCommand(authService, messages));
        register(cm, "setpassword", new SetPasswordCommand(resolver, authService, messages));
        register(cm, "resetpassword", new ResetPasswordCommand(resolver, authService, messages));

        register(cm, "accounts", new AccountsCommand(resolver, altsService, messages));
        register(cm, "ipaccounts", new IpAccountsCommand(altsService, messages));
        register(cm, "alts", new AltsCommand(resolver, altsService, messages));

        register(cm, "zavyncore", new ZavynCoreCommand(proxy, logger, messages, database, floodgate, executor,
                () -> {
                    try {
                        this.config = PluginConfig.load(dataDirectory);
                        this.messages = Messages.load(dataDirectory);
                        logger.info("[ZavynCore] Configuracao recarregada.");
                    } catch (Exception e) {
                        logger.error("[ZavynCore] Falha ao recarregar configuracao: {}", e.getMessage());
                    }
                }));
    }

    private void register(CommandManager cm, String name, com.velocitypowered.api.command.SimpleCommand command) {
        CommandMeta meta = cm.metaBuilder(name).plugin(this).build();
        cm.register(meta, command);
    }
}
