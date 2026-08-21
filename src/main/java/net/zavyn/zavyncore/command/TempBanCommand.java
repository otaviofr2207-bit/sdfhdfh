package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Arrays;
import java.util.Map;

/** /tempban <jogador> <tempo> [motivo] */
public final class TempBanCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final TargetResolver resolver;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public TempBanCommand(ProxyServer proxy, TargetResolver resolver, PunishmentService punishmentService, Messages messages) {
        this.proxy = proxy;
        this.resolver = resolver;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 2) {
            source.sendMessage(Msg.parse(messages.get("usage.tempban")));
            return;
        }

        String targetName = args[0];
        long duration;
        try {
            duration = TimeUtil.parse(args[1]);
        } catch (IllegalArgumentException e) {
            source.sendMessage(Msg.parse(messages.get("errors.invalid-duration")));
            return;
        }
        if (duration < 0) {
            source.sendMessage(Msg.parse(messages.get("errors.tempban-needs-duration")));
            return;
        }

        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : messages.get("defaults.no-reason");
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            var target = optionalTarget.get();
            punishmentService.ban(target.uuid(), target.name(), target.lastIp(), reason, staffName, duration)
                    .thenAccept(punishment -> {
                        source.sendMessage(Msg.parse(messages.get("ban.success"), Map.of(
                                "player", target.name(), "reason", reason,
                                "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                                "punishment_id", punishment.punishmentId())));
                        proxy.getPlayer(target.name()).ifPresent(p -> p.disconnect(Msg.parse(
                                messages.get("ban.temporary"), Map.of("reason", reason,
                                        "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                                        "punishment_id", punishment.punishmentId(), "staff", staffName))));
                    });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.tempban");
    }
}
