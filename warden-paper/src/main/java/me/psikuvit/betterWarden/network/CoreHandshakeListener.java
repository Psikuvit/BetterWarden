package me.psikuvit.betterWarden.network;

import me.psikuvit.betterWarden.core.network.CoreHandshake;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Receives the {@code warden:core} handshake a Stage 3 proxy sends when a player first connects
 * to this backend, and caches it to disk. Only takes effect on the *next* boot (warden.mode:
 * client) - BetterWarden.java already decided HOST vs CLIENT for this run before any player
 * could have joined and triggered this.
 */
public class CoreHandshakeListener implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final File cacheFile;

    public CoreHandshakeListener(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.cacheFile = new File(dataFolder, "node-handshake.yml");
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte @NonNull [] message) {
        if (!CoreHandshake.CHANNEL_ID.equals(channel)) {
            return;
        }
        CoreHandshake handshake;
        try {
            handshake = CoreHandshake.fromBytes(message);
        } catch (IOException e) {
            plugin.getLogger().severe("Received a malformed core handshake: " + e.getMessage());
            return;
        }
        persist(handshake);
        plugin.getLogger().info("Received and cached a proxy core handshake (core=" + handshake.coreUrl()
                + "). Set warden.mode: client and restart to use it.");
    }

    private void persist(CoreHandshake handshake) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("core-url", handshake.coreUrl());
        data.put("node-token", handshake.nodeToken());
        data.put("version", handshake.version());
        data.put("tier", handshake.tier());
        data.put("cached-at", Instant.now().toString());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try (Writer writer = new FileWriter(cacheFile, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(data, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not cache proxy handshake: " + e.getMessage());
        }
    }

    /** Called on a HOST boot: tells the operator a proxy relationship is cached but unused, in case that's not intentional. */
    public static void warnIfCached(JavaPlugin plugin, File dataFolder) {
        readCached(dataFolder).ifPresent(handshake -> plugin.getLogger().warning(
                "A proxy core handshake is cached (core=" + handshake.coreUrl() + ") but warden.mode is "
                        + "'host' - this server is running its own independent core. Set warden.mode: "
                        + "client and restart if you meant to defer to that proxy's core instead."));
    }

    /** Read back on a CLIENT boot - see BetterWarden.java. */
    public static Optional<CoreHandshake> readCached(File dataFolder) {
        File cacheFile = new File(dataFolder, "node-handshake.yml");
        if (!cacheFile.exists()) {
            return Optional.empty();
        }
        try (FileInputStream in = new FileInputStream(cacheFile)) {
            Map<String, Object> data = new Yaml().load(in);
            if (data == null) {
                return Optional.empty();
            }
            return Optional.of(new CoreHandshake(
                    String.valueOf(data.get("core-url")), String.valueOf(data.get("node-token")),
                    String.valueOf(data.get("version")), String.valueOf(data.get("tier"))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
