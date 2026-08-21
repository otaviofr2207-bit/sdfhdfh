-- =========================================================
--  ZavynCore - schema de referencia (MySQL/MariaDB)
--  O plugin cria estas tabelas automaticamente ao iniciar
--  (ver Database.migrate()). Este arquivo e apenas para
--  quem quiser inspecionar ou provisionar o banco manualmente.
-- =========================================================

CREATE DATABASE IF NOT EXISTS zavyncore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE zavyncore;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS player_ips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    first_seen BIGINT NOT NULL,
    last_seen BIGINT NOT NULL,
    UNIQUE KEY uq_player_ip (uuid, ip),
    INDEX idx_player_ips_ip (ip),
    CONSTRAINT fk_player_ips_player FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS warnings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_uuid CHAR(36) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    staff_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_warnings_target (target_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sessions (
    uuid CHAR(36) PRIMARY KEY,
    logged_in TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS security_confirmations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid CHAR(36) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    confirmed_at BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    INDEX idx_confirmations_uuid (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ip_bans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip VARCHAR(45) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    staff_name VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_ip_bans_ip (ip, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_name VARCHAR(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_name VARCHAR(16) NULL,
    details VARCHAR(255) NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_admin_logs_staff (staff_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
