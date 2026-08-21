package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.util.Msg;

/** /register <senha> <senha> - apenas jogadores online, so faz sentido para contas offline. */
public final class RegisterCommand implements SimpleCommand {

    private final AuthService authService;
    private final Messages messages;

    public RegisterCommand(AuthService authService, Messages messages) {
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
        if (args.length < 2) {
            player.sendMessage(Msg.parse(messages.get("usage.register")));
            return;
        }
        char[] password = args[0].toCharArray();
        char[] confirm = args[1].toCharArray();

        authService.register(player.getUniqueId(), password, confirm).thenAccept(result -> {
            if (result.success()) {
                player.sendMessage(Msg.parse(messages.get("auth.register-success")));
            } else {
                player.sendMessage(Msg.parse(messages.get("auth.register-failed"),
                        java.util.Map.of("error", result.errorMessage())));
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof Player;
    }
}
