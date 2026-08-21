package net.zavyn.zavyncore.database.dao;

import net.zavyn.zavyncore.database.Database;
import net.zavyn.zavyncore.model.Warning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WarnDao {

    private final Database database;

    public WarnDao(Database database) {
        this.database = database;
    }

    public Warning insert(UUID uuid, String name, String reason, String staffName) throws SQLException {
        String sql = "INSERT INTO warnings (target_uuid, target_name, reason, staff_name, created_at) VALUES (?,?,?,?,?)";
        long now = System.currentTimeMillis();
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, reason);
            ps.setString(4, staffName);
            ps.setLong(5, now);
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return new Warning(id, uuid, name, reason, staffName, now);
        }
    }

    public List<Warning> list(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM warnings WHERE target_uuid = ? ORDER BY created_at DESC";
        List<Warning> out = new ArrayList<>();
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Warning(rs.getLong("id"), uuid, rs.getString("target_name"),
                            rs.getString("reason"), rs.getString("staff_name"), rs.getLong("created_at")));
                }
            }
        }
        return out;
    }

    public int count(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM warnings WHERE target_uuid = ?";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void clear(UUID uuid) throws SQLException {
        String sql = "DELETE FROM warnings WHERE target_uuid = ?";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }
}
