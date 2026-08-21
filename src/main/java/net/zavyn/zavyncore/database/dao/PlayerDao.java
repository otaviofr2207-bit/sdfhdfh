package net.zavyn.zavyncore.database.dao;

import net.zavyn.zavyncore.database.Database;
import net.zavyn.zavyncore.model.AccountType;
import net.zavyn.zavyncore.model.PlayerAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDao {

    private final Database database;

    public PlayerDao(Database database) {
        this.database = database;
    }

    public Optional<PlayerAccount> find(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        }
    }

    public Optional<UUID> findUuidByName(String name) throws SQLException {
        String sql = "SELECT uuid FROM players WHERE name = ? ORDER BY last_seen DESC LIMIT 1";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(UUID.fromString(rs.getString(1)));
            }
        }
    }

    /** Cria o registro do jogador na primeira conexao, ou atualiza nome/last_seen/last_ip se ja existir. */
    public PlayerAccount upsertOnConnect(UUID uuid, String name, AccountType accountType, String ip) throws SQLException {
        Optional<PlayerAccount> existing = find(uuid);
        long now = System.currentTimeMillis();

        if (existing.isPresent()) {
            String sql = "UPDATE players SET name = ?, last_ip = ?, last_seen = ? WHERE uuid = ?";
            try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, ip);
                ps.setLong(3, now);
                ps.setString(4, uuid.toString());
                ps.executeUpdate();
            }
            PlayerAccount account = existing.get();
            account.setName(name);
            account.setLastIp(ip);
            account.setLastSeen(now);
            return account;
        } else {
            String sql = """
                INSERT INTO players (uuid, name, account_type, password_hash, last_confirmation_at, last_ip, first_seen, last_seen)
                VALUES (?,?,?,NULL,?,?,?,?)
                """;
            try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, accountType.name());
                ps.setLong(4, now);
                ps.setString(5, ip);
                ps.setLong(6, now);
                ps.setLong(7, now);
                ps.executeUpdate();
            }
            return new PlayerAccount(uuid, name, accountType, null, now, ip, now, now);
        }
    }

    public void setPasswordHash(UUID uuid, String hash) throws SQLException {
        String sql = "UPDATE players SET password_hash = ? WHERE uuid = ?";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void updateLastConfirmation(UUID uuid, long timestamp) throws SQLException {
        String sql = "UPDATE players SET last_confirmation_at = ? WHERE uuid = ?";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, timestamp);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    /** Registra (ou atualiza) o vinculo uuid<->ip em player_ips. */
    public void recordIp(UUID uuid, String ip) throws SQLException {
        String sql = """
            INSERT INTO player_ips (uuid, ip, first_seen, last_seen) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen)
            """;
        long now = System.currentTimeMillis();
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
        }
    }

    /** Igual a recordIp, mas usa uma conexao/transacao ja aberta pelo chamador (ver AccountLimitService). */
    public void recordIp(Connection connection, UUID uuid, String ip) throws SQLException {
        String sql = """
            INSERT INTO player_ips (uuid, ip, first_seen, last_seen) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen)
            """;
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
        }
    }

    public boolean hasEverConnectedFromIp(UUID uuid, String ip) throws SQLException {
        String sql = "SELECT 1 FROM player_ips WHERE uuid = ? AND ip = ? LIMIT 1";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Conta quantas contas OFFLINE distintas ja usaram este IP.
     * Usado dentro de uma transacao com lock (ver AccountLimitService) para evitar race conditions.
     */
    public int countOfflineAccountsForIp(Connection connection, String ip, UUID excludingSelf) throws SQLException {
        String sql = """
            SELECT COUNT(DISTINCT p.uuid) FROM players p
            JOIN player_ips pi ON pi.uuid = p.uuid
            WHERE pi.ip = ? AND p.account_type = 'OFFLINE' AND p.uuid <> ?
            FOR UPDATE
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, excludingSelf == null ? "" : excludingSelf.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<String> namesForIp(String ip) throws SQLException {
        String sql = """
            SELECT DISTINCT p.name FROM players p
            JOIN player_ips pi ON pi.uuid = p.uuid
            WHERE pi.ip = ? ORDER BY p.name
            """;
        List<String> out = new ArrayList<>();
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    public List<String> ipsForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT ip FROM player_ips WHERE uuid = ? ORDER BY last_seen DESC";
        List<String> out = new ArrayList<>();
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    public void setLoggedIn(UUID uuid, boolean loggedIn) throws SQLException {
        String sql = """
            INSERT INTO sessions (uuid, logged_in, last_login_at) VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE logged_in = VALUES(logged_in), last_login_at = VALUES(last_login_at)
            """;
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setBoolean(2, loggedIn);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private PlayerAccount map(ResultSet rs) throws SQLException {
        return new PlayerAccount(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getString("password_hash"),
                rs.getLong("last_confirmation_at"),
                rs.getString("last_ip"),
                rs.getLong("first_seen"),
                rs.getLong("last_seen")
        );
    }
}
