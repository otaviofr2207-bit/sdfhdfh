package net.zavyn.zavyncore.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrega e expoe config.yml de forma tipada. Copia o arquivo padrao do jar
 * na primeira execucao caso ainda nao exista na pasta de dados do plugin.
 */
public final class PluginConfig {

    private final Map<String, Object> root;

    private PluginConfig(Map<String, Object> root) {
        this.root = root;
    }

    @SuppressWarnings("unchecked")
    public static PluginConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path configFile = dataDirectory.resolve("config.yml");

        if (!Files.exists(configFile)) {
            try (InputStream in = PluginConfig.class.getResourceAsStream("/config.yml")) {
                if (in == null) {
                    throw new IOException("config.yml padrao nao encontrado dentro do jar");
                }
                Files.copy(in, configFile);
            }
        }

        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configFile)) {
            Map<String, Object> loaded = yaml.load(in);
            return new PluginConfig(loaded == null ? new LinkedHashMap<>() : loaded);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) return new LinkedHashMap<>();
            current = ((Map<String, Object>) current).get(part);
        }
        return current instanceof Map ? (Map<String, Object>) current : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, T defaultValue) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) return defaultValue;
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return defaultValue;
        }
        try {
            return (T) current;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public String getString(String path, String def) { return get(path, def); }
    public boolean getBoolean(String path, boolean def) { return get(path, def); }
    public int getInt(String path, int def) {
        Object v = get(path, (Object) def);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }
    public String getStringDuration(String path, String def) { return get(path, def); }
}
