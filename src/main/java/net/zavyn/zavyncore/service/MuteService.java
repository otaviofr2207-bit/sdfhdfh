package net.zavyn.zavyncore.service;

import net.zavyn.zavyncore.database.dao.LogDao;
import net.zavyn.zavyncore.database.dao.PunishmentDao;
import net.zavyn.zavyncore.model.Punishment;
import net.zavyn.zavyncore.model.PunishmentType;
import net.zavyn.zavyncore.util.IdGenerator;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Sistema de mute global. Reaproveita a tabela "punishments" com os tipos MUTE/TEMP_MUTE,
 * o que garante que o mute vale em qualquer servidor por tras do Velocity (Lobby, FullPvP, BedWars...).
 */
public final class MuteService {

    private final PunishmentDao punishmentDao;
    private final LogDao logDao;
    private final Executor executor;

    public MuteService(PunishmentDao punishmentDao, LogDao logDao, Executor executor) {
        this.punishmentDao = punishmentDao;
        this.logDao = logDao;
        this.executor = executor;
    }

    public CompletableFuture<Punishment> mute(UUID targetUuid, String targetName, String reason,
                                               String staffName, long durationMillis) {
        PunishmentType type = durationMillis < 0 ? PunishmentType.MUTE : PunishmentType.TEMP_MUTE;
        return CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                long expiresAt = durationMillis < 0 ? -1L : now + durationMillis;
                String id = IdGenerator.punishmentId(type.prefix());
                Punishment punishment = punishmentDao.insert(id, type, targetUuid, targetName, null,
                        reason, staffName, now, expiresAt);
                logDao.log(staffName, type.name(), targetName, reason);
                return punishment;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> unmute(UUID targetUuid, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean revoked = punishmentDao.revokeActive(targetUuid, staffName, PunishmentType.MUTE, PunishmentType.TEMP_MUTE);
                if (revoked) logDao.log(staffName, "UNMUTE", targetUuid.toString(), null);
                return revoked;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Optional<Punishment>> checkMute(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return punishmentDao.findActiveByUuid(uuid, PunishmentType.MUTE, PunishmentType.TEMP_MUTE);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
}
