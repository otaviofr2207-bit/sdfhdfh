package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.service.ConfirmationService;
import net.zavyn.zavyncore.util.Msg;

/** /login <senha> */
public final class LoginCommand implements SimpleCommand {

    private final AuthService authService;
    private final ConfirmationService confirmationService;
    private final Messages messages;

    public LoginCommand(AuthService authService, ConfirmationService confirmationService, Messages messages) {
        this.authService = authService;
        this.confirmationService = confirmationService;
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
            player.sendMessage(Msg.parse(messages.get("usage.login")));
            return;
        }
        char[] password = args[0].toCharArray();

        authService.login(player.getUniqueId(), password).thenAccept(result -> {
            if (result.success()) {
                player.sendMessage(Msg.parse(messages.get("auth.login-success")));
                try {
                    confirmationService.confirm(player.getUniqueId());
                } catch (Exception ignored) {
                }
            } else {
                player.sendMessage(Msg.parse(messages.get("auth.login-failed"),
                        java.util.Map.of("error", result.errorMessage())));
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof Player;
    }
}
