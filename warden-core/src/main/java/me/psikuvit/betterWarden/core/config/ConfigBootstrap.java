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
        System.setProperty("spring.config.import", configFile.toURI().toString());

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
            case "mysql" -> {
                Map<String, Object> mysql = asMap(storage.get("mysql"));
                // Env vars win when present - avoids config.yml and a container-orchestrator's
                // own secret (docker-compose .env, Kubernetes Secret, ...) disagreeing on the
                // password. config.yml's value is still the fallback for a non-containerized run.
                String host = env("WARDEN_DB_HOST", String.valueOf(mysql.getOrDefault("host", "localhost")));
                String mysqlPort = env("WARDEN_DB_PORT", String.valueOf(mysql.getOrDefault("port", 3306)));
                String database = env("WARDEN_DB_NAME", String.valueOf(mysql.getOrDefault("database", "warden")));
                String username = env("WARDEN_DB_USER", String.valueOf(mysql.getOrDefault("username", "warden")));
                String password = env("WARDEN_DB_PASSWORD", String.valueOf(mysql.getOrDefault("password", "")));

                String url = "jdbc:mysql://" + host + ":" + mysqlPort + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                System.setProperty("spring.datasource.url", url);
                System.setProperty("spring.datasource.username", username);
                System.setProperty("spring.datasource.password", password);
                System.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
                System.setProperty("spring.flyway.locations", "classpath:db/migration/mysql");
                // No spring.jpa.database-platform override here - Hibernate auto-detects the
                // right MySQLDialect from the JDBC connection, unlike SQLite which needs the
                // community-dialects override above since Hibernate has no first-party support.
            }
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
     * Reads warden.mode before Spring boots: HOST boots the full local core, CLIENT connects to
     * an external core instead (core.client.RemoteCoreClient) and never touches local storage.
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
     * warden-velocity's own CLIENT mode reads its core URL directly from config.yml (warden.core.url)
     * - unlike warden-paper's CLIENT mode, there's no upstream proxy to hand it one via a handshake;
     * a Velocity proxy in CLIENT mode IS the top of the topology, pointed at a standalone core.
     */
    public static String readCoreUrl(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> warden = asMap(root.get("warden"));
        Map<String, Object> core = asMap(warden.get("core"));
        return String.valueOf(core.getOrDefault("url", ""));
    }

    /** Velocity's CLIENT-mode login gate needs this without booting Spring - see WardenVelocityPlugin.java. */
    public static boolean readLoginGateFailOpen(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> warden = asMap(root.get("warden"));
        Map<String, Object> loginGate = asMap(warden.get("login-gate"));
        return Boolean.parseBoolean(String.valueOf(loginGate.getOrDefault("fail-open", true)));
    }

    /** CLIENT mode needs its own node-token to present, without booting Spring - see ensureSecuritySecrets. */
    public static String readNodeToken(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> warden = asMap(root.get("warden"));
        Map<String, Object> security = asMap(warden.get("security"));
        return String.valueOf(security.getOrDefault("node-token", ""));
    }

    /** CLIENT mode needs the salt for IpHashingService without booting Spring - see BetterWarden.java. */
    public static String readIpSalt(File configFile) throws IOException {
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(configFile)) {
            root = new Yaml().load(in);
        }
        Map<String, Object> warden = asMap(root.get("warden"));
        Map<String, Object> security = asMap(warden.get("security"));
        return String.valueOf(security.getOrDefault("ip-salt", ""));
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

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
