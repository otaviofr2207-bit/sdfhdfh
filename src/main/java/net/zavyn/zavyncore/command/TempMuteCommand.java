package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.MuteService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Arrays;
import java.util.Map;

/** /tempmute <jogador> <tempo> [motivo] */
public final class TempMuteCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final MuteService muteService;
    private final Messages messages;

    public TempMuteCommand(TargetResolver resolver, MuteService muteService, Messages messages) {
        this.resolver = resolver;
        this.muteService = muteService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 2) {
            source.sendMessage(Msg.parse(messages.get("usage.tempmute")));
            return;
        }
        String targetName = args[0];
        long duration;
        try {
            duration = TimeUtil.parse(args[1]);
        } catch (IllegalArgumentException e) {
            source.sendMessage(Msg.parse(messages.get("errors.invalid-duration")));
            return;
        }
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : messages.get("defaults.no-reason");
        String staffName = source instanceof com.velocitypowered.api.proxy.Player p ? p.getUsername() : "CONSOLE";

        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            var target = optionalTarget.get();
            muteService.mute(target.uuid(), target.name(), reason, staffName, duration).thenAccept(punishment ->
                    source.sendMessage(Msg.parse(messages.get("mute.success"), Map.of(
                            "player", target.name(), "reason", reason,
                            "duration", TimeUtil.formatRemaining(punishment.remainingMillis()),
                            "punishment_id", punishment.punishmentId()))));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.tempmute");
    }
}
