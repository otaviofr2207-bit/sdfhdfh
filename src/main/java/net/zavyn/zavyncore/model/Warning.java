package net.zavyn.zavyncore.model;

import java.util.UUID;

public final class Warning {
    private final long id;
    private final UUID targetUuid;
    private final String targetName;
    private final String reason;
    private final String staffName;
    private final long createdAt;

    public Warning(long id, UUID targetUuid, String targetName, String reason,
                    String staffName, long createdAt) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.reason = reason;
        this.staffName = staffName;
        this.createdAt = createdAt;
    }

    public long id() { return id; }
    public UUID targetUuid() { return targetUuid; }
    public String targetName() { return targetName; }
    public String reason() { return reason; }
    public String staffName() { return staffName; }
    public long createdAt() { return createdAt; }
}
