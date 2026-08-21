package net.zavyn.zavyncore.service;

import net.zavyn.zavyncore.database.dao.IpBanDao;
import net.zavyn.zavyncore.database.dao.LogDao;
import net.zavyn.zavyncore.database.dao.PunishmentDao;
import net.zavyn.zavyncore.model.Punishment;
import net.zavyn.zavyncore.model.PunishmentType;
import net.zavyn.zavyncore.util.IdGenerator;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Sistema de ban / tempban / unban / banip / unbanip / checkban / history.
 * Toda operacao de banco roda fora da thread principal do Velocity (Executor assincrono).
 */
public final class PunishmentService {

    private final PunishmentDao punishmentDao;
    private final IpBanDao ipBanDao;
    private final LogDao logDao;
    private final Executor executor;
    private final boolean banAlsoIp;

    public PunishmentService(PunishmentDao punishmentDao, IpBanDao ipBanDao, LogDao logDao,
                              Executor executor, boolean banAlsoIp) {
        this.punishmentDao = punishmentDao;
        this.ipBanDao = ipBanDao;
        this.logDao = logDao;
        this.executor = executor;
        this.banAlsoIp = banAlsoIp;
    }

    public CompletableFuture<Punishment> ban(UUID targetUuid, String targetName, String targetIp,
                                              String reason, String staffName, long durationMillis) {
        PunishmentType type = durationMillis < 0 ? PunishmentType.BAN : PunishmentType.TEMP_BAN;
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                long expiresAt = durationMillis < 0 ? -1L : now + durationMillis;
                String id = IdGenerator.punishmentId(type.prefix());
                Punishment punishment = punishmentDao.insert(id, type, targetUuid, targetName, targetIp,
                        reason, staffName, now, expiresAt);

                if (banAlsoIp && targetIp != null) {
                    ipBanDao.insert(targetIp, "Auto (ban-also-ip): " + reason, staffName, now, expiresAt);
                }

                logDao.log(staffName, type.name(), targetName, reason);
                return punishment;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> unban(UUID targetUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean revoked = punishmentDao.revokeActive(targetUuid, staffName, PunishmentType.BAN, PunishmentType.TEMP_BAN);
                if (revoked) logDao.log(staffName, "UNBAN", targetUuid.toString(), null);
                return revoked;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> banIp(String ip, String reason, String staffName, long durationMillis) {
        return CompletableFuture.runAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                long expiresAt = durationMillis < 0 ? -1L : now + durationMillis;
                ipBanDao.insert(ip, reason, staffName, now, expiresAt);
                logDao.log(staffName, "IPBAN", ip, reason);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> unbanIp(String ip, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean revoked = ipBanDao.revoke(ip);
                if (revoked) logDao.log(staffName, "UNBANIP", ip, null);
                return revoked;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> kick(String targetName, String staffName, String reason) {
        return CompletableFuture.runAsync(() -> logDao.log(staffName, "KICK", targetName, reason), executor);
    }

    /** @return a punicao de ban ativa, ou null se o jogador nao estiver banido. */
    public CompletableFuture<Punishment> checkBan(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return punishmentDao.findActiveByUuid(uuid, PunishmentType.BAN, PunishmentType.TEMP_BAN).orElse(null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> checkIpBanned(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ipBanDao.isBanned(ip);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<List<Punishment>> history(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return punishmentDao.history(uuid);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
}
