package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.util.Msg;

/** /changepassword <senha_antiga> <senha_nova> */
public final class ChangePasswordCommand implements SimpleCommand {

    private final AuthService authService;
    private final Messages messages;

    public ChangePasswordCommand(AuthService authService, Messages messages) {
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
            player.sendMessage(Msg.parse(messages.get("usage.changepassword")));
            return;
        }
        authService.changePassword(player.getUniqueId(), args[0].toCharArray(), args[1].toCharArray())
                .thenAccept(result -> player.sendMessage(Msg.parse(
                        result.success() ? messages.get("auth.changepassword-success") : messages.get("auth.changepassword-failed"),
                        java.util.Map.of("error", result.errorMessage() == null ? "" : result.errorMessage()))));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof Player;
    }
}
