package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Arrays;
import java.util.Map;

/** /banip <ip> [motivo] */
public final class BanIpCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public BanIpCommand(ProxyServer proxy, PunishmentService punishmentService, Messages messages) {
        this.proxy = proxy;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.banip")));
            return;
        }
        String ip = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.get("defaults.no-reason");
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        punishmentService.banIp(ip, reason, staffName, -1L).thenRun(() -> {
            source.sendMessage(Msg.parse(messages.get("banip.success"), Map.of("ip", ip, "reason", reason)));
            proxy.getAllPlayers().stream()
                    .filter(p -> p.getRemoteAddress() != null && p.getRemoteAddress().getAddress() != null
                            && ip.equals(p.getRemoteAddress().getAddress().getHostAddress()))
                    .forEach(p -> p.disconnect(Msg.parse(messages.get("ban.ip-banned"), Map.of("ip", ip))));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.ipban");
    }
}
