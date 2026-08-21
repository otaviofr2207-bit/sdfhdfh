package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /unban <jogador> */
public final class UnbanCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public UnbanCommand(TargetResolver resolver, PunishmentService punishmentService, Messages messages) {
        this.resolver = resolver;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.unban")));
            return;
        }
        String targetName = args[0];
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            punishmentService.unban(optionalTarget.get().uuid(), staffName).thenAccept(revoked -> {
                String key = revoked ? "unban.success" : "unban.not-banned";
                source.sendMessage(Msg.parse(messages.get(key), Map.of("player", targetName)));
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.unban");
    }
}
