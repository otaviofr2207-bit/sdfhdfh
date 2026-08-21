package net.zavyn.zavyncore.model;

import java.util.UUID;

/**
 * Representa uma punicao (ban, tempban, mute, tempmute, ipban, kick, warn).
 * expiresAt == -1 significa punicao permanente.
 */
public final class Punishment {

    private final long id;
    private final String punishmentId;
    private final PunishmentType type;
    private final UUID targetUuid;
    private final String targetName;
    private final String targetIp;
    private final String reason;
    private final String staffName;
    private final long createdAt;
    private final long expiresAt;
    private boolean active;
    private String revokedBy;
    private long revokedAt;

    public Punishment(long id, String punishmentId, PunishmentType type, UUID targetUuid,
                       String targetName, String targetIp, String reason, String staffName,
                       long createdAt, long expiresAt, boolean active,
                       String revokedBy, long revokedAt) {
        this.id = id;
        this.punishmentId = punishmentId;
        this.type = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetIp = targetIp;
        this.reason = reason;
        this.staffName = staffName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.revokedBy = revokedBy;
        this.revokedAt = revokedAt;
    }

    public long id() { return id; }
    public String punishmentId() { return punishmentId; }
    public PunishmentType type() { return type; }
    public UUID targetUuid() { return targetUuid; }
    public String targetName() { return targetName; }
    public String targetIp() { return targetIp; }
    public String reason() { return reason; }
    public String staffName() { return staffName; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public boolean active() { return active; }
    public String revokedBy() { return revokedBy; }
    public long revokedAt() { return revokedAt; }

    public boolean isPermanent() {
        return expiresAt < 0;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }

    public void markRevoked(String revokedBy) {
        this.active = false;
        this.revokedBy = revokedBy;
        this.revokedAt = System.currentTimeMillis();
    }

    public long remainingMillis() {
        if (isPermanent()) return -1L;
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
}
