package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.AltsService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /ipaccounts <ip> - apenas administradores, ve todas as contas ligadas a um IP. */
public final class IpAccountsCommand implements SimpleCommand {

    private final AltsService altsService;
    private final Messages messages;

    public IpAccountsCommand(AltsService altsService, Messages messages) {
        this.altsService = altsService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.ipaccounts")));
            return;
        }
        String ip = args[0];
        altsService.namesForIp(ip).thenAccept(names ->
                source.sendMessage(Msg.parse(messages.get("ipaccounts.result"), Map.of(
                        "ip", ip, "accounts", String.join(", ", names)))));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.admin");
    }
}
