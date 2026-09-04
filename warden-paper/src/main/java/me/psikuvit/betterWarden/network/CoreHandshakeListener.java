package me.psikuvit.betterWarden.network;

import me.psikuvit.betterWarden.core.network.CoreHandshake;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives the {@code warden:core} handshake a Stage 3 proxy sends when a player first connects
 * to this backend. Caches it to disk - there's no CLIENT mode to switch into yet (see PLAN.md),
 * so this only ever warns, it never changes what this server actually does.
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
        plugin.getLogger().warning("Received a proxy core handshake (core=" + handshake.coreUrl()
                + ") but CLIENT mode isn't implemented yet - this server is still running its own "
                + "independent HOST core. Cached for when CLIENT mode ships (see PLAN.md Stage 3).");
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

    /** Logs a startup warning if a handshake was cached in a previous session - see class doc. */
    @SuppressWarnings("unchecked")
    public static void warnIfCached(JavaPlugin plugin, File dataFolder) {
        File cacheFile = new File(dataFolder, "node-handshake.yml");
        if (!cacheFile.exists()) {
            return;
        }
        try {
            Map<String, Object> data = (Map<String, Object>) new Yaml().load(new java.io.FileInputStream(cacheFile));
            Object coreUrl = data == null ? null : data.get("core-url");
            plugin.getLogger().warning("A proxy core handshake is cached from a previous session (core=" + coreUrl
                    + ") but CLIENT mode isn't implemented yet - running as an independent HOST core.");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read cached proxy handshake (" + cacheFile + "): " + e.getMessage());
        }
    }
}
