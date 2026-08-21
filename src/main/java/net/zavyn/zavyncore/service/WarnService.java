package net.zavyn.zavyncore.service;

import net.zavyn.zavyncore.config.PluginConfig;
import net.zavyn.zavyncore.database.dao.LogDao;
import net.zavyn.zavyncore.database.dao.WarnDao;
import net.zavyn.zavyncore.model.Warning;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Sistema de warns com acoes automaticas configuraveis (ex: 3 warns -> mute, 5 warns -> tempban).
 * As acoes automaticas ficam em config.yml, secao "warnings.auto-actions".
 */
public final class WarnService {

    private final WarnDao warnDao;
    private final LogDao logDao;
    private final Executor executor;
    private final PluginConfig config;
    private final MuteService muteService;
    private final PunishmentService punishmentService;

    public WarnService(WarnDao warnDao, LogDao logDao, Executor executor, PluginConfig config,
                        MuteService muteService, PunishmentService punishmentService) {
        this.warnDao = warnDao;
        this.logDao = logDao;
        this.executor = executor;
        this.config = config;
        this.muteService = muteService;
        this.punishmentService = punishmentService;
    }

    public CompletableFuture<WarnResult> warn(UUID uuid, String name, String reason, String staffName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Warning warning = warnDao.insert(uuid, name, reason, staffName);
                int total = warnDao.count(uuid);
                logDao.log(staffName, "WARN", name, reason + " (total=" + total + ")");

                String autoAction = resolveAutoAction(total);
                if (autoAction != null) {
                    applyAutoAction(autoAction, uuid, name, "Acao automatica: " + total + " warns");
                }
                return new WarnResult(warning, total, autoAction);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private String resolveAutoAction(int totalWarns) {
        Map<String, Object> actions = config.get("warnings.auto-actions", Map.of());
        Object action = actions.get(String.valueOf(totalWarns));
        return action == null ? null : String.valueOf(action);
    }

    private void applyAutoAction(String action, UUID uuid, String name, String reason) {
        // Formato esperado: "mute" | "mute:1d" | "tempban:7d" | "ban" | "kick"
        String[] parts = action.split(":", 2);
        String kind = parts[0].trim().toLowerCase();
        String durationRaw = parts.length > 1 ? parts[1].trim() : null;

        switch (kind) {
            case "mute" -> {
                long duration = durationRaw == null ? -1 : net.zavyn.zavyncore.util.TimeUtil.parse(durationRaw);
                muteService.mute(uuid, name, reason, "ZavynCore-AutoAction", duration);
            }
            case "tempban", "ban" -> {
                long duration = durationRaw == null ? -1 : net.zavyn.zavyncore.util.TimeUtil.parse(durationRaw);
                punishmentService.ban(uuid, name, null, reason, "ZavynCore-AutoAction", duration);
            }
            case "kick" -> punishmentService.kick(name, "ZavynCore-AutoAction", reason);
            default -> logDao.log("ZavynCore-AutoAction", "UNKNOWN_ACTION", name, action);
        }
    }

    public CompletableFuture<List<Warning>> list(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return warnDao.list(uuid);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> clear(UUID uuid, String staffName) {
        return CompletableFuture.runAsync(() -> {
            try {
                warnDao.clear(uuid);
                logDao.log(staffName, "CLEARWARNINGS", uuid.toString(), null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public record WarnResult(Warning warning, int totalWarns, String autoActionApplied) {
    }
}
