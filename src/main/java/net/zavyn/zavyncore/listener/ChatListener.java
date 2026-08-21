package net.zavyn.zavyncore.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.zavyn.zavyncore.config.Messages;
import net.zavyn.zavyncore.model.Punishment;
import net.zavyn.zavyncore.service.AuthService;
import net.zavyn.zavyncore.service.MuteService;
import net.zavyn.zavyncore.util.Msg;
import net.zavyn.zavyncore.util.TimeUtil;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Intercepta o chat no Velocity para aplicar mute global (item 4) e bloquear
 * mensagens de jogadores offline que ainda nao concluiram /login (item 9).
 */
public final class ChatListener {

    private final Messages messages;
    private final MuteService muteService;
    private final AuthService authService;

    public ChatListener(Messages messages, MuteService muteService, AuthService authService) {
        this.messages = messages;
        this.muteService = muteService;
        this.authService = authService;
    }

    @Subscribe
    public EventTask onChat(PlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!authService.isLoggedIn(player.getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(Msg.parse(messages.get("auth.chat-blocked")));
            return null;
        }

        CompletableFuture<Punishment> future = muteService.checkMute(player.getUniqueId())
                .thenApply(optional -> optional.orElse(null));

        return EventTask.resumeWhenComplete(future.thenAccept(mute -> {
            if (mute == null) return;
            event.setResult(PlayerChatEvent.ChatResult.denied());
            player.sendMessage(Msg.parse(
                    mute.isPermanent() ? messages.get("mute.permanent") : messages.get("mute.temporary"),
                    Map.of(
                            "reason", mute.reason(),
                            "duration", TimeUtil.formatRemaining(mute.remainingMillis()),
                            "punishment_id", mute.punishmentId()
                    )));
        }));
    }
}
