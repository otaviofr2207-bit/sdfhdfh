package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Map;

/** /history <jogador> */
public final class HistoryCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public HistoryCommand(TargetResolver resolver, PunishmentService punishmentService, Messages messages) {
        this.resolver = resolver;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.history")));
            return;
        }
        String targetName = args[0];
        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            punishmentService.history(optionalTarget.get().uuid()).thenAccept(list -> {
                if (list.isEmpty()) {
                    source.sendMessage(Msg.parse(messages.get("history.empty"), Map.of("player", targetName)));
                    return;
                }
                source.sendMessage(Msg.parse(messages.get("history.header"), Map.of("player", targetName)));
                for (var p : list) {
                    Component line = Msg.parse(messages.get("history.entry"), Map.of(
                            "punishment_id", p.punishmentId(),
                            "type", p.type().name(),
                            "reason", p.reason(),
                            "staff", p.staffName(),
                            "duration", TimeUtil.formatRemaining(p.remainingMillis()),
                            "status", p.active() ? "ativa" : "revogada/expirada"
                    ));
                    source.sendMessage(line);
                }
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.history");
    }
}
