package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Map;

/**
 * /ban <jogador> [motivo]
 * Tambem aceita "/ban <jogador> 30d motivo" (o primeiro token do motivo, se for uma duracao valida,
 * transforma o ban em temporario) - conforme exemplo do pedido.
 */
public final class BanCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final TargetResolver resolver;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public BanCommand(ProxyServer proxy, TargetResolver resolver, PunishmentService punishmentService, Messages messages) {
        this.proxy = proxy;
        this.resolver = resolver;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.ban")));
            return;
        }

        String targetName = args[0];
        long duration = -1L;
        int reasonStart = 1;
        if (args.length >= 2) {
            try {
                duration = TimeUtil.parse(args[1]);
                reasonStart = 2;
            } catch (IllegalArgumentException ignored) {
                // primeiro token nao e uma duracao valida - trata tudo a partir de args[1] como motivo, ban permanente
            }
        }
        String reason = args.length > reasonStart
                ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length))
                : messages.get("defaults.no-reason");

        String staffName = invocation.source() instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";
        long finalDuration = duration;

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            var target = optionalTarget.get();
            punishmentService.ban(target.uuid(), target.name(), target.lastIp(), reason, staffName, finalDuration)
                    .thenAccept(punishment -> {
                        source.sendMessage(Msg.parse(messages.get("ban.success"), Map.of(
                                "player", target.name(),
                                "reason", reason,
                                "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                                "punishment_id", punishment.punishmentId()
                        )));
                        proxy.getPlayer(target.name()).ifPresent(p -> p.disconnect(Msg.parse(
                                punishment.isPermanent() ? messages.get("ban.permanent") : messages.get("ban.temporary"),
                                Map.of("reason", reason, "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                                        "punishment_id", punishment.punishmentId(), "staff", staffName))));
                    });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.ban");
    }
}
