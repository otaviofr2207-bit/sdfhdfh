package net.zavyn.zavyncore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.zavyn.zavyncore.config.PluginConfig;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerencia o pool de conexoes (HikariCP) e a criacao automatica do schema.
 */
public final class Database {

    private final HikariDataSource dataSource;
    private final Logger logger;

    public Database(PluginConfig config, Logger logger) {
        this.logger = logger;

        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "zavyncore");
        String username = config.getString("database.username", "root");
        String password = config.getString("database.password", "");
        int maxPoolSize = config.getInt("database.pool.maximum-pool-size", 10);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC");
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setPoolName("ZavynCore-Pool");
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Cria todas as tabelas necessarias caso ainda nao existam.
     */
    public void migrate() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid CHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    account_type VARCHAR(16) NOT NULL,
                    password_hash VARCHAR(255) NULL,
                    last_confirmation_at BIGINT NOT NULL DEFAULT 0,
                    last_ip VARCHAR(45) NULL,
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    INDEX idx_players_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS player_ips (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid CHAR(36) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    UNIQUE KEY uq_player_ip (uuid, ip),
                    INDEX idx_player_ips_ip (ip),
                    CONSTRAINT fk_player_ips_player FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS punishments (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    punishment_id VARCHAR(32) NOT NULL UNIQUE,
                    type VARCHAR(16) NOT NULL,
                    target_uuid CHAR(36) NULL,
                    target_name VARCHAR(16) NULL,
                    target_ip VARCHAR(45) NULL,
                    reason VARCHAR(255) NOT NULL,
                    staff_name VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    active TINYINT(1) NOT NULL DEFAULT 1,
                    revoked_by VARCHAR(16) NULL,
                    revoked_at BIGINT NULL,
                    INDEX idx_punishments_target (target_uuid, type, active),
                    INDEX idx_punishments_ip (target_ip, type, active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS warnings (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    target_uuid CHAR(36) NOT NULL,
                    target_name VARCHAR(16) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    staff_name VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    INDEX idx_warnings_target (target_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    uuid CHAR(36) PRIMARY KEY,
                    logged_in TINYINT(1) NOT NULL DEFAULT 0,
                    last_login_at BIGINT NOT NULL DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS security_confirmations (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    uuid CHAR(36) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    confirmed_at BIGINT NOT NULL,
                    reason VARCHAR(32) NOT NULL,
                    INDEX idx_confirmations_uuid (uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS ip_bans (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    ip VARCHAR(45) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    staff_name VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    active TINYINT(1) NOT NULL DEFAULT 1,
                    INDEX idx_ip_bans_ip (ip, active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS admin_logs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    staff_name VARCHAR(16) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    target_name VARCHAR(16) NULL,
                    details VARCHAR(255) NULL,
                    created_at BIGINT NOT NULL,
                    INDEX idx_admin_logs_staff (staff_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }

        logger.info("[ZavynCore] Schema do banco de dados verificado/criado com sucesso.");
    }
}
