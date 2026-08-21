package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.WarnService;
import net.zavyn.zavyncore.util.Msg;

import java.time.Instant;
import java.util.Map;

/** /warnings <jogador> */
public final class WarningsCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final WarnService warnService;
    private final Messages messages;

    public WarningsCommand(TargetResolver resolver, WarnService warnService, Messages messages) {
        this.resolver = resolver;
        this.warnService = warnService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.warnings")));
            return;
        }
        String targetName = args[0];
        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            warnService.list(optionalTarget.get().uuid()).thenAccept(list -> {
                if (list.isEmpty()) {
                    source.sendMessage(Msg.parse(messages.get("warnings.empty"), Map.of("player", targetName)));
                    return;
                }
                source.sendMessage(Msg.parse(messages.get("warnings.header"),
                        Map.of("player", targetName, "total", String.valueOf(list.size()))));
                for (var w : list) {
                    source.sendMessage(Msg.parse(messages.get("warnings.entry"), Map.of(
                            "reason", w.reason(), "staff", w.staffName(),
                            "date", Instant.ofEpochMilli(w.createdAt()).toString())));
                }
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.warn");
    }
}
