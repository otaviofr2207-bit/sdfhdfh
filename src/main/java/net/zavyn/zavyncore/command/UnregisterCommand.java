package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.util.Msg;

/** /unregister <senha> */
public final class UnregisterCommand implements SimpleCommand {

    private final AuthService authService;
    private final Messages messages;

    public UnregisterCommand(AuthService authService, Messages messages) {
        this.authService = authService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Msg.parse(messages.get("errors.players-only")));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length < 1) {
            player.sendMessage(Msg.parse(messages.get("usage.unregister")));
            return;
        }
        authService.unregister(player.getUniqueId(), args[0].toCharArray()).thenAccept(result -> {
            if (result.success()) {
                player.sendMessage(Msg.parse(messages.get("auth.unregister-success")));
                player.disconnect(Msg.parse(messages.get("auth.unregister-disconnect")));
            } else {
                player.sendMessage(Msg.parse(messages.get("auth.unregister-failed"),
                        java.util.Map.of("error", result.errorMessage())));
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof Player;
    }
}
