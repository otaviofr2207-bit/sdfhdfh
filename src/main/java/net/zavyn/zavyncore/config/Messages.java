package net.zavyn.zavyncore.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrega messages.yml (todas as mensagens do plugin, em formato MiniMessage).
 */
public final class Messages {

    private final Map<String, Object> root;

    private Messages(Map<String, Object> root) {
        this.root = root;
    }

    public static Messages load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("messages.yml");

        if (!Files.exists(file)) {
            try (InputStream in = Messages.class.getResourceAsStream("/messages.yml")) {
                if (in == null) {
                    throw new IOException("messages.yml padrao nao encontrado dentro do jar");
                }
                Files.copy(in, file);
            }
        }

        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> loaded = yaml.load(in);
            return new Messages(loaded == null ? new LinkedHashMap<>() : loaded);
        }
    }

    @SuppressWarnings("unchecked")
    public String get(String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) return "<red>Mensagem ausente: " + path + "</red>";
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return "<red>Mensagem ausente: " + path + "</red>";
        }
        return String.valueOf(current);
    }

    public String prefix() {
        return get("prefix");
    }
}
