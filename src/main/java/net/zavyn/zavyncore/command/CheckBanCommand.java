package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Map;

/** /checkban <jogador> */
public final class CheckBanCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public CheckBanCommand(TargetResolver resolver, PunishmentService punishmentService, Messages messages) {
        this.resolver = resolver;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.checkban")));
            return;
        }
        String targetName = args[0];
        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            punishmentService.checkBan(optionalTarget.get().uuid()).thenAccept(punishment -> {
                if (punishment == null) {
                    source.sendMessage(Msg.parse(messages.get("checkban.not-banned"), Map.of("player", targetName)));
                    return;
                }
                source.sendMessage(Msg.parse(messages.get("checkban.banned"), Map.of(
                        "player", targetName,
                        "reason", punishment.reason(),
                        "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                        "punishment_id", punishment.punishmentId(),
                        "staff", punishment.staffName())));
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.history");
    }
}
