package net.zavyn.zavyncore.model;

import java.util.UUID;

/**
 * Registro persistido de um jogador conhecido pelo ZavynCore.
 */
public final class PlayerAccount {

    private final UUID uuid;
    private String name;
    private final AccountType accountType;
    private String passwordHash; // null se nao exigir login (premium/floodgate com auto-login)
    private boolean loggedIn;
    private long lastConfirmationAt;
    private String lastIp;
    private long firstSeen;
    private long lastSeen;

    public PlayerAccount(UUID uuid, String name, AccountType accountType, String passwordHash,
                          long lastConfirmationAt, String lastIp, long firstSeen, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.accountType = accountType;
        this.passwordHash = passwordHash;
        this.lastConfirmationAt = lastConfirmationAt;
        this.lastIp = lastIp;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountType accountType() { return accountType; }
    public String passwordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isRegistered() { return passwordHash != null; }
    public boolean loggedIn() { return loggedIn; }
    public void setLoggedIn(boolean loggedIn) { this.loggedIn = loggedIn; }
    public long lastConfirmationAt() { return lastConfirmationAt; }
    public void setLastConfirmationAt(long lastConfirmationAt) { this.lastConfirmationAt = lastConfirmationAt; }
    public String lastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public long firstSeen() { return firstSeen; }
    public long lastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
