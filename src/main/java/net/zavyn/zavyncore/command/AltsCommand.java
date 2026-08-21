package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AltsService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /alts <jogador> - alias administrativo de /accounts, pensado para investigacao de ban evasion. */
public final class AltsCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final AltsService altsService;
    private final Messages messages;

    public AltsCommand(TargetResolver resolver, AltsService altsService, Messages messages) {
        this.resolver = resolver;
        this.altsService = altsService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.alts")));
            return;
        }
        String targetName = args[0];
        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            altsService.accountsSharingIpWith(optionalTarget.get().uuid()).thenAccept(names ->
                    source.sendMessage(Msg.parse(messages.get("alts.result"), Map.of(
                            "player", targetName, "accounts", String.join(", ", names)))));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.admin");
    }
}
