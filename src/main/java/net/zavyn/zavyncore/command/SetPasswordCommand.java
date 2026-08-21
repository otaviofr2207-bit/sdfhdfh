package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /setpassword <jogador> <nova_senha> - permissao zavyncore.password.set */
public final class SetPasswordCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final AuthService authService;
    private final Messages messages;

    public SetPasswordCommand(TargetResolver resolver, AuthService authService, Messages messages) {
        this.resolver = resolver;
        this.authService = authService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 2) {
            source.sendMessage(Msg.parse(messages.get("usage.setpassword")));
            return;
        }
        String targetName = args[0];
        char[] newPassword = args[1].toCharArray();
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            var target = optionalTarget.get();
            authService.adminSetPassword(target.uuid(), target.name(), newPassword, staffName).thenAccept(result ->
                    source.sendMessage(Msg.parse(
                            result.success() ? messages.get("auth.setpassword-success") : messages.get("auth.setpassword-failed"),
                            Map.of("player", target.name(), "error", result.errorMessage() == null ? "" : result.errorMessage()))));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.password.set");
    }
}
