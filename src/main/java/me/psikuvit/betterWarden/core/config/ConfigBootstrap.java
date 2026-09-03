package me.psikuvit.betterWarden.core.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Runs before the Spring context exists: ensures config.yml exists, resolves boot-time system properties. */
public final class ConfigBootstrap {

    private ConfigBootstrap() {
    }

    public static File ensureConfigFile(File dataFolder, Supplier<InputStream> defaultConfigResource) throws IOException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IOException("Could not create data folder: " + dataFolder);
        }
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = defaultConfigResource.get()) {
                if (in == null) {
                    throw new IOException("default-config.yml missing from the plugin jar");
                }
                Files.copy(in, configFile.toPath());
            }
        }
        return configFile;
    }

    @SuppressWarnings("unchecked")
    public static void applyToSystemProperties(File configFile, File dataFolder) throws IOException {
        // Makes config.yml itself a Spring property source, so CoreConfig's
        // @ConfigurationProperties(prefix = "warden") actually binds from it.
        System.setProperty("spring.config.additional-location", "file:" + configFile.getAbsolutePath());

        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> warden = asMap(root.get("warden"));
        Map<String, Object> storage = asMap(warden.get("storage"));
        String type = String.valueOf(storage.getOrDefault("type", "sqlite"));

        switch (type.toLowerCase()) {
            case "sqlite" -> {
                File dbFile = new File(dataFolder, "warden.db");
                System.setProperty("spring.datasource.url", "jdbc:sqlite:" + dbFile.getAbsolutePath());
                System.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
                System.setProperty("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
                System.setProperty("spring.flyway.locations", "classpath:db/migration/sqlite");
            }
            case "mysql" -> throw new UnsupportedOperationException(
                    "storage.type: mysql is not wired up yet. Set storage.type back to sqlite.");
            default -> throw new IllegalStateException("Unknown storage.type '" + type + "' in config.yml");
        }

        Map<String, Object> panel = asMap(warden.get("panel"));
        Object port = panel.get("port");
        if (port != null) {
            System.setProperty("server.port", String.valueOf(port));
        }
    }

    /** Generates and persists warden.security.ip-salt into config.yml on first run, if it isn't set already. */
    @SuppressWarnings("unchecked")
    public static void ensureIpSalt(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            Object loaded = new Yaml().load(in);
            root = loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        }
        Map<String, Object> warden = getOrCreateMap(root, "warden");
        Map<String, Object> security = getOrCreateMap(warden, "security");

        Object existing = security.get("ip-salt");
        if (existing instanceof String s && !s.isBlank()) {
            return;
        }

        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        security.put("ip-salt", HexFormat.of().formatHex(randomBytes));

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try (Writer writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(root, writer);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getOrCreateMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    /** Re-parses config.yml to confirm it's still valid YAML. Does not re-bind Spring beans - datasource/port changes still need a restart. */
    public static void validate(File configFile) throws IOException {
        try (InputStream in = new FileInputStream(configFile)) {
            new Yaml().load(in);
        }
    }

    private static Map<String, Object> asMap(Object o) {
        return o == null ? Map.of() : (Map<String, Object>) o;
    }
}
