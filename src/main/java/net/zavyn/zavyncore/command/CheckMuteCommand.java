package net.zavyn.zavyncore.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.service.MuteService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Map;

/** /checkmute <jogador> */
public final class CheckMuteCommand implements SimpleCommand {

    private final TargetResolver resolver;
    private final MuteService muteService;
    private final Messages messages;

    public CheckMuteCommand(TargetResolver resolver, MuteService muteService, Messages messages) {
        this.resolver = resolver;
        this.muteService = muteService;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(Msg.parse(messages.get("usage.checkmute")));
            return;
        }
        String targetName = args[0];
        resolver.resolve(targetName).thenAccept(optionalTarget -> {
            if (optionalTarget.isEmpty()) {
                source.sendMessage(Msg.parse(messages.get("errors.player-not-found"), Map.of("player", targetName)));
                return;
            }
            muteService.checkMute(optionalTarget.get().uuid()).thenAccept(opt -> {
                if (opt.isEmpty()) {
                    source.sendMessage(Msg.parse(messages.get("checkmute.not-muted"), Map.of("player", targetName)));
                    return;
                }
                var m = opt.get();
                source.sendMessage(Msg.parse(messages.get("checkmute.muted"), Map.of(
                        "player", targetName, "reason", m.reason(),
                        "duration", TimeUtil.formatRemaining(m.remainingMillis()),
                        "punishment_id", m.punishmentId(), "staff", m.staffName())));
            });
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("zavyncore.mute");
    }
}
