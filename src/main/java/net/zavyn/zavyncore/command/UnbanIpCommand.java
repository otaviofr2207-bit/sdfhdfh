package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Map;

/** /unbanip <ip> */
public final class UnbanIpCommand implements SimpleCommand {

    private final PunishmentService punishmentService;
    private final Messages messages;

    public UnbanIpCommand(PunishmentService punishmentService, Messages messages) {
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.unbanip")));
            return;
        }
        String ip = args[0];
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";
        punishmentService.unbanIp(ip, staffName).thenAccept(revoked -> {
            String key = revoked ? "unbanip.success" : "unbanip.not-banned";
            source.sendMessage(Msg.parse(messages.get(key), Map.of("ip", ip)));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.unipban");
    }
}
