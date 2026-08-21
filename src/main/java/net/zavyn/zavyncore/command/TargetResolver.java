package net.zavyn.zavyncore.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.zavyn.zavyncore.database.dao.PlayerDao;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Resolve o alvo de um comando administrativo (/ban, /mute, /warn, ...) tanto para
 * jogadores online no proxy quanto para jogadores offline ja conhecidos pelo banco.
 */
public final class TargetResolver {

    private final ProxyServer proxy;
    private final PlayerDao playerDao;
    private final Executor executor;

    public TargetResolver(ProxyServer proxy, PlayerDao playerDao, Executor executor) {
        this.proxy = proxy;
        this.playerDao = playerDao;
        this.executor = executor;
    }

    public CompletableFuture<Optional<Target>> resolve(String name) {
        Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            Player p = online.get();
            String ip = p.getRemoteAddress() != null && p.getRemoteAddress().getAddress() != null
                    ? p.getRemoteAddress().getAddress().getHostAddress() : null;
            return CompletableFuture.completedFuture(Optional.of(new Target(p.getUniqueId(), p.getUsername(), ip, p)));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<UUID> uuid = playerDao.findUuidByName(name);
                if (uuid.isEmpty()) return Optional.<Target>empty();
                return playerDao.find(uuid.get())
                        .map(acc -> new Target(acc.uuid(), acc.name(), acc.lastIp(), null));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public record Target(UUID uuid, String name, String lastIp, Player onlinePlayer) {
        public boolean isOnline() { return onlinePlayer != null; }
    }
}
