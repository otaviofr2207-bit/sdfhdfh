package net.zavyn.zavyncore.model;

public enum PunishmentType {
    BAN("BAN"),
    TEMP_BAN("TBAN"),
    IP_BAN("IPBAN"),
    MUTE("MUTE"),
    TEMP_MUTE("TMUTE"),
    KICK("KICK"),
    WARN("WARN");

    private final String prefix;

    PunishmentType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
