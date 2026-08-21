package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.MuteService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /unmute <jogador> */
public final class UnmuteCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final MuteService muteService;
    private final Messages messages;

    public UnmuteCommand(TargetResolver resolver, MuteService muteService, Messages messages) {
        this.resolver = resolver;
        this.muteService = muteService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.unmute")));
            return;
        }
        String targetName = args[0];
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            muteService.unmute(optionalTarget.get().uuid(), staffName).thenAccept(revoked -> {
                String key = revoked ? "unmute.success" : "unmute.not-muted";
                source.sendMessage(Msg.parse(messages.get(key), Map.of("player", targetName)));
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.unmute");
    }
}
