package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.WarnService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /clearwarnings <jogador> */
public final class ClearWarningsCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final WarnService warnService;
    private final Messages messages;

    public ClearWarningsCommand(TargetResolver resolver, WarnService warnService, Messages messages) {
        this.resolver = resolver;
        this.warnService = warnService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.clearwarnings")));
            return;
        }
        String targetName = args[0];
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";
        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            warnService.clear(optionalTarget.get().uuid(), staffName).thenRun(() ->
                    source.sendMessage(Msg.parse(messages.get("clearwarnings.success"), Map.of("player", targetName))));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.warn");
    }
}
