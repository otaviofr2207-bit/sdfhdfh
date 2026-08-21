package net.zavyn.zavyncore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

/**
 * Helper para converter mensagens configuraveis (MiniMessage) com placeholders
 * do tipo {player}, {reason}, etc. em Component.
 */
public final class Msg {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Msg() {
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(raw);
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String replaced = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace("{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return MINI.deserialize(replaced);
    }

    public static String replace(String raw, Map<String, String> placeholders) {
        String replaced = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replaced = replaced.replace("{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return replaced;
    }
}
