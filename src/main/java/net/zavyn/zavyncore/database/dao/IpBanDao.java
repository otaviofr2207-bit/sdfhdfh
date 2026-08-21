package net.zavyn.zavyncore.database.dao;

import net.zavyn.zavyncore.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class IpBanDao {

    private final Database database;

    public IpBanDao(Database database) {
        this.database = database;
    }

    public void insert(String ip, String reason, String staffName, long createdAt, long expiresAt) throws SQLException {
        String sql = "INSERT INTO ip_bans (ip, reason, staff_name, created_at, expires_at, active) VALUES (?,?,?,?,?,1)";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, reason);
            ps.setString(3, staffName);
            ps.setLong(4, createdAt);
            ps.setLong(5, expiresAt);
            ps.executeUpdate();
        }
    }

    public boolean isBanned(String ip) throws SQLException {
        String sql = "SELECT expires_at FROM ip_bans WHERE ip = ? AND active = 1 ORDER BY created_at DESC LIMIT 1";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                long expiresAt = rs.getLong(1);
                if (expiresAt >= 0 && System.currentTimeMillis() >= expiresAt) {
                    revoke(ip);
                    return false;
                }
                return true;
            }
        }
    }

    public boolean revoke(String ip) throws SQLException {
        String sql = "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            return ps.executeUpdate() > 0;
        }
    }
}
