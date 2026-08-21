package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.util.Msg;

/** /logout */
public final class LogoutCommand implements SimpleCommand {

    private final AuthService authService;
    private final Messages messages;

    public LogoutCommand(AuthService authService, Messages messages) {
        this.authService = authService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Msg.parse(messages.get("errors.players-only")));
            return;
        }
        authService.logout(player.getUniqueId(), player.getUsername());
        player.sendMessage(Msg.parse(messages.get("auth.logout-success")));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source() instanceof Player;
    }
}
