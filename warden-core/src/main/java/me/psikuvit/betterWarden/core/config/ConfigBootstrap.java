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
import java.util.Locale;
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
        Object bindAddress = panel.get("bind-address");
        if (bindAddress != null && !String.valueOf(bindAddress).isBlank()) {
            System.setProperty("server.address", String.valueOf(bindAddress));
        }
        boolean trustForwardedHeaders = Boolean.parseBoolean(String.valueOf(panel.getOrDefault("trust-forwarded-headers", false)));
        System.setProperty("server.forward-headers-strategy", trustForwardedHeaders ? "native" : "none");
    }

    /**
     * Reads warden.mode before Spring boots, so a backend can skip booting its own HOST core
     * entirely when it's explicitly configured as a proxy CLIENT (see PLAN.md Stage 3 - CLIENT
     * mode itself, the REST/WS client, isn't built yet; this only decides whether HOST boots).
     */
    public static String readMode(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> warden = asMap(root.get("warden"));
        return String.valueOf(warden.getOrDefault("mode", "HOST")).toUpperCase(Locale.ROOT);
    }

    /**
     * Generates and persists warden.security.ip-salt and warden.security.node-token into
     * config.yml on first run, for whichever of the two isn't already set. node-token is the
     * shared secret this node presents in the Stage 3 proxy handshake (docs/spec, CoreHandshake) -
     * no real per-node auth yet, see PLAN.md.
     */
    @SuppressWarnings("unchecked")
    public static void ensureSecuritySecrets(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            Object loaded = new Yaml().load(in);
            root = loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        }
        Map<String, Object> warden = getOrCreateMap(root, "warden");
        Map<String, Object> security = getOrCreateMap(warden, "security");

        boolean changed = false;
        changed |= ensureRandomSecret(security, "ip-salt");
        changed |= ensureRandomSecret(security, "node-token");
        if (!changed) {
            return;
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try (Writer writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(root, writer);
        }
    }

    private static boolean ensureRandomSecret(Map<String, Object> security, String key) {
        Object existing = security.get(key);
        if (existing instanceof String s && !s.isBlank()) {
            return false;
        }
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        security.put(key, HexFormat.of().formatHex(randomBytes));
        return true;
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o == null ? Map.of() : (Map<String, Object>) o;
    }
}
