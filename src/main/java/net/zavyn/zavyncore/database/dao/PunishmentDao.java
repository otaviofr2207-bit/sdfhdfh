package net.zavyn.zavyncore.database.dao;

import net.zavyn.zavyncore.database.Database;
import net.zavyn.zavyncore.model.Punishment;
import net.zavyn.zavyncore.model.PunishmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PunishmentDao {

    private final Database database;

    public PunishmentDao(Database database) {
        this.database = database;
    }

    public Punishment insert(String punishmentId, PunishmentType type, UUID targetUuid, String targetName,
                              String targetIp, String reason, String staffName, long createdAt, long expiresAt)
            throws SQLException {
        String sql = """
            INSERT INTO punishments (punishment_id, type, target_uuid, target_name, target_ip,
                                      reason, staff_name, created_at, expires_at, active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """;
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, punishmentId);
            ps.setString(2, type.name());
            ps.setString(3, targetUuid == null ? null : targetUuid.toString());
            ps.setString(4, targetName);
            ps.setString(5, targetIp);
            ps.setString(6, reason);
            ps.setString(7, staffName);
            ps.setLong(8, createdAt);
            ps.setLong(9, expiresAt);
            ps.executeUpdate();

            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return new Punishment(id, punishmentId, type, targetUuid, targetName, targetIp,
                    reason, staffName, createdAt, expiresAt, true, null, 0);
        }
    }

    public Optional<Punishment> findActiveByUuid(UUID uuid, PunishmentType... types) throws SQLException {
        return findActive("target_uuid", uuid.toString(), types);
    }

    public Optional<Punishment> findActiveByIp(String ip, PunishmentType... types) throws SQLException {
        return findActive("target_ip", ip, types);
    }

    private Optional<Punishment> findActive(String column, String value, PunishmentType... types) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM punishments WHERE " + column + " = ? AND active = 1");
        if (types.length > 0) {
            sql.append(" AND type IN (");
            for (int i = 0; i < types.length; i++) {
                sql.append(i == 0 ? "?" : ",?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY created_at DESC LIMIT 1");

        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setString(1, value);
            for (int i = 0; i < types.length; i++) {
                ps.setString(i + 2, types[i].name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Punishment p = map(rs);
                if (p.isExpired()) {
                    expire(p.id());
                    return Optional.empty();
                }
                return Optional.of(p);
            }
        }
    }

    public List<Punishment> history(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM punishments WHERE target_uuid = ? ORDER BY created_at DESC LIMIT 100";
        List<Punishment> list = new ArrayList<>();
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public boolean revokeActive(UUID uuid, String revokedBy, PunishmentType... types) throws SQLException {
        return revoke("target_uuid", uuid.toString(), revokedBy, types);
    }

    public boolean revokeActiveIp(String ip, String revokedBy, PunishmentType... types) throws SQLException {
        return revoke("target_ip", ip, revokedBy, types);
    }

    private boolean revoke(String column, String value, String revokedBy, PunishmentType... types) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "UPDATE punishments SET active = 0, revoked_by = ?, revoked_at = ? WHERE " + column + " = ? AND active = 1");
        if (types.length > 0) {
            sql.append(" AND type IN (");
            for (int i = 0; i < types.length; i++) sql.append(i == 0 ? "?" : ",?");
            sql.append(")");
        }
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setString(1, revokedBy);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, value);
            for (int i = 0; i < types.length; i++) ps.setString(i + 4, types[i].name());
            return ps.executeUpdate() > 0;
        }
    }

    public void expire(long id) throws SQLException {
        String sql = "UPDATE punishments SET active = 0, revoked_by = 'SYSTEM-EXPIRED', revoked_at = ? WHERE id = ?";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private Punishment map(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString("target_uuid");
        return new Punishment(
                rs.getLong("id"),
                rs.getString("punishment_id"),
                PunishmentType.valueOf(rs.getString("type")),
                uuidStr == null ? null : UUID.fromString(uuidStr),
                rs.getString("target_name"),
                rs.getString("target_ip"),
                rs.getString("reason"),
                rs.getString("staff_name"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getBoolean("active"),
                rs.getString("revoked_by"),
                rs.getLong("revoked_at")
        );
    }
}
