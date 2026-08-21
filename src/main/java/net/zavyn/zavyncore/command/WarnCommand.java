package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.WarnService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Arrays;
import java.util.Map;

/** /warn <jogador> [motivo] */
public final class WarnCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final WarnService warnService;
    private final Messages messages;

    public WarnCommand(TargetResolver resolver, WarnService warnService, Messages messages) {
        this.resolver = resolver;
        this.warnService = warnService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.warn")));
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.get("defaults.no-reason");
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            var target = optionalTarget.get();
            warnService.warn(target.uuid(), target.name(), reason, staffName).thenAccept(result -> {
                source.sendMessage(Msg.parse(messages.get("warn.success"), Map.of(
                        "player", target.name(), "reason", reason, "total", String.valueOf(result.totalWarns()))));
                if (result.autoActionApplied() != null) {
                    source.sendMessage(Msg.parse(messages.get("warn.auto-action"), Map.of(
                            "player", target.name(), "action", result.autoActionApplied())));
                }
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.warn");
    }
}
