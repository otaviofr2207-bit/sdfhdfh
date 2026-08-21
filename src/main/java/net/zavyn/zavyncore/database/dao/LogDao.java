package net.zavyn.zavyncore.database.dao;

import net.zavyn.zavyncore.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Registra acoes administrativas. Nunca deve receber senhas.
 */
public final class LogDao {

    private final Database database;

    public LogDao(Database database) {
        this.database = database;
    }

    public void log(String staffName, String action, String targetName, String details) {
        String sql = "INSERT INTO admin_logs (staff_name, action, target_name, details, created_at) VALUES (?,?,?,?,?)";
        try (Connection c = database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, staffName);
            ps.setString(2, action);
            ps.setString(3, targetName);
            ps.setString(4, details);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Nao deixamos falha de log derrubar a acao administrativa, apenas registramos no console.
            System.err.println("[ZavynCore] Falha ao gravar admin log: " + e.getMessage());
        }
    }
}
