package net.zavyn.zavyncore.service;

import net.zavyn.zavyncore.database.dao.PlayerDao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Consultas administrativas: /accounts, /ipaccounts, /alts.
 * IPs nunca sao expostos para jogadores comuns - apenas nomes de possiveis contas relacionadas.
 */
public final class AltsService {

    private final PlayerDao playerDao;
    private final Executor executor;

    public AltsService(PlayerDao playerDao, Executor executor) {
        this.playerDao = playerDao;
        this.executor = executor;
    }

    public CompletableFuture<List<String>> accountsSharingIpWith(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> ips = playerDao.ipsForPlayer(uuid);
                return ips.stream()
                        .flatMap(ip -> {
                            try {
                                return playerDao.namesForIp(ip).stream();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .distinct()
                        .toList();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<List<String>> namesForIp(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return playerDao.namesForIp(ip);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<List<String>> ipsForPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return playerDao.ipsForPlayer(uuid);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
}
