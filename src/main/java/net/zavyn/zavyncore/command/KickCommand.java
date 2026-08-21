package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.PunishmentService;
import net.zavyn.zavyncore.util.Msg;

import java.util.Arrays;
import java.util.Map;

/** /kick <jogador> [motivo] */
public final class KickCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final PunishmentService punishmentService;
    private final Messages messages;

    public KickCommand(ProxyServer proxy, PunishmentService punishmentService, Messages messages) {
        this.proxy = proxy;
        this.punishmentService = punishmentService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.kick")));
            return;
        }
        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : messages.get("defaults.no-reason");
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        var target = proxy.getPlayer(targetName);
        if (target.isEmpty()) {
            source.sendMessage(Msg.parse(messages.get("errors.player-not-online"), Map.of("player", targetName)));
            return;
        }
        target.get().disconnect(Msg.parse(messages.get("kick.message"), Map.of("reason", reason)));
        punishmentService.kick(targetName, staffName, reason).thenRun(() ->
                source.sendMessage(Msg.parse(messages.get("kick.success"), Map.of("player", targetName, "reason", reason))));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.kick");
    }
}
