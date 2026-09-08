package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.core.client.RemoteCoreClient;
import me.psikuvit.betterWarden.core.client.RemotePunishmentCache;
import me.psikuvit.betterWarden.core.client.WriteJournal;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.network.CoreHandshake;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import me.psikuvit.betterWarden.network.CoreHandshakeListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;

/**
 * Config/mode bootstrap and CLIENT-mode core construction, shared verbatim by BetterWarden.java
 * (Paper) and spigot/WardenSpigotPlugin.java - none of it touches any Paper- or Spigot-specific
 * API, only plain JavaPlugin methods and warden-core's own platform-free classes. Extracted after
 * the two files were found to have drifted (Spigot was calling
 * CoreHandshakeListener.warnIfCached() unconditionally, before the mode check, instead of only
 * once CLIENT mode was ruled out like Paper's copy does - a real bug from copy-paste, not a
 * deliberate difference) - one copy of this logic instead of two rules that out going forward.
 */
public final class PlatformBootstrap {

    private PlatformBootstrap() {
    }

    public record ConfigResult(File configFile, String mode) {
    }

    /**
     * Ensures config.yml exists, generates security secrets, applies boot-time system properties,
     * registers the CoreHandshake plugin-message channel, and reads warden.mode. Returns null (and
     * disables the plugin, having already logged why) on any failure - callers should just
     * {@code return;} when this returns null, exactly like the single-plugin version used to.
     */
    public static ConfigResult initConfig(JavaPlugin plugin) {
        File configFile;
        try {
            configFile = ConfigBootstrap.ensureConfigFile(plugin.getDataFolder(), () -> plugin.getResource("default-config.yml"));
            ConfigBootstrap.ensureSecuritySecrets(configFile);
            ConfigBootstrap.applyToSystemProperties(configFile, plugin.getDataFolder());
        } catch (Exception e) {
            plugin.getLogger().severe("Could not load config.yml: " + e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return null;
        }

        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CoreHandshake.CHANNEL_ID,
                new CoreHandshakeListener(plugin, plugin.getDataFolder()));

        String mode;
        try {
            mode = ConfigBootstrap.readMode(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not read warden.mode from config.yml: " + e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return null;
        }
        return new ConfigResult(configFile, mode);
    }

    public record ClientCore(RemoteCoreClient remoteClient, RemotePunishmentCache cache, LangService lang,
                              IpHashingService ipHashing, MojangApiService mojangApi) {
    }

    /**
     * No local core at all - never boots Spring/JPA/SQLite. Just enough to talk to a proxy's core
     * over REST/WS: config.yml is read raw for the IP salt, LangService/MojangApiService are plain
     * POJOs (no Spring needed), and the returned RemotePunishmentCache lets the same gate listeners
     * as HOST mode run against it instead of a local PunishmentService. Starts the client
     * (connects async) before returning. coreLogger/journalLogger are caller-supplied rather than
     * resolved here - Paper's getSLF4JLogger() and plain Spigot's explicit
     * org.slf4j.LoggerFactory.getLogger(...) are kept as each platform already had them, not
     * unified without a reason to.
     */
    public static ClientCore bootClientCore(CoreHandshake handshake, File configFile, File dataFolder,
                                             Logger coreLogger, Logger journalLogger) throws Exception {
        LangService lang = new LangService();
        CoreConfig rawConfig = new CoreConfig();
        rawConfig.getSecurity().setIpSalt(ConfigBootstrap.readIpSalt(configFile));
        IpHashingService ipHashing = new IpHashingService(rawConfig);

        RemotePunishmentCache cache = new RemotePunishmentCache();
        WriteJournal journal = new WriteJournal(dataFolder, journalLogger);
        RemoteCoreClient remoteClient = new RemoteCoreClient(handshake.coreUrl(), handshake.nodeToken(), cache, journal, coreLogger);
        remoteClient.start();

        return new ClientCore(remoteClient, cache, lang, ipHashing, new MojangApiService());
    }
}
