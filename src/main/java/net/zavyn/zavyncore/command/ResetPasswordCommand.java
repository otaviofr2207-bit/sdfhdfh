package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.util.Msg;

import java.security.SecureRandom;
import java.util.Map;

/**
 * /resetpassword <jogador> - gera uma nova senha aleatoria e a exibe UMA VEZ para o staff,
 * ja que o requisito e nao expor a senha antiga (nao existe senha antiga a mostrar,
 * apenas a nova, gerada agora).
 */
public final class ResetPasswordCommand implements SimpleCommand {

    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TargetResolver resolver;
    private final AuthService authService;
    private final Messages messages;

    public ResetPasswordCommand(TargetResolver resolver, AuthService authService, Messages messages) {
        this.resolver = resolver;
        this.authService = authService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.resetpassword")));
            return;
        }
        String targetName = args[0];
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";
        String generated = generatePassword();

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            var target = optionalTarget.get();
            authService.adminSetPassword(target.uuid(), target.name(), generated.toCharArray(), staffName)
                    .thenAccept(result -> source.sendMessage(Msg.parse(
                            result.success() ? messages.get("auth.resetpassword-success") : messages.get("auth.resetpassword-failed"),
                            Map.of("player", target.name(), "new_password", generated))));
        });
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.password.reset");
    }
}
