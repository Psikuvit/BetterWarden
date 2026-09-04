package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.bridge.SpigotBridge;
import me.psikuvit.betterWarden.client.RemotePunishmentCommands;
import me.psikuvit.betterWarden.command.PunishmentCommands;
import me.psikuvit.betterWarden.command.SpigotMsg;
import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.client.RemoteCoreClient;
import me.psikuvit.betterWarden.core.client.RemotePunishmentCache;
import me.psikuvit.betterWarden.core.client.WriteJournal;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.network.CoreHandshake;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import me.psikuvit.betterWarden.listener.BanGateListener;
import me.psikuvit.betterWarden.listener.MuteCommandBlockListener;
import me.psikuvit.betterWarden.listener.MuteGateListener;
import me.psikuvit.betterWarden.listener.PlayerTrackingListener;
import me.psikuvit.betterWarden.network.CoreHandshakeListener;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;

/**
 * Plain Spigot (non-Paper) backend plugin - not a variant of warden-paper, a full separate
 * module against a completely different API surface (no Brigadier, no native Adventure, no
 * Folia). HOST mode boots the same embedded Spring context as every other platform; CLIENT mode
 * talks to an external core over REST/WS. Ships the core moderation surface only this pass -
 * PLAN.md tracks what's deferred (reports/tickets/lookup/GUI menus/chat filter/hooks).
 */
public final class WardenSpigotPlugin extends JavaPlugin {

    private ConfigurableApplicationContext springContext;
    private RemoteCoreClient remoteClient;
    private BukkitAudiences audiences;
    private File configFile;

    @Override
    public void onEnable() {
        // Same reason as every other platform module: Spring's autoconfiguration scanning reads
        // the context classloader, and Bukkit's plugin classloader is never the default one.
        Thread.currentThread().setContextClassLoader(getClassLoader());

        audiences = BukkitAudiences.create(this);
        SpigotMsg msg = new SpigotMsg(audiences);

        try {
            configFile = ConfigBootstrap.ensureConfigFile(getDataFolder(), () -> getResource("default-config.yml"));
            ConfigBootstrap.ensureSecuritySecrets(configFile);
            ConfigBootstrap.applyToSystemProperties(configFile, getDataFolder());
        } catch (Exception e) {
            getLogger().severe("Could not load config.yml: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getMessenger().registerIncomingPluginChannel(this, CoreHandshake.CHANNEL_ID,
                new CoreHandshakeListener(this, getDataFolder()));
        CoreHandshakeListener.warnIfCached(this, getDataFolder());

        String mode;
        try {
            mode = ConfigBootstrap.readMode(configFile);
        } catch (Exception e) {
            getLogger().severe("Could not read warden.mode from config.yml: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if ("CLIENT".equals(mode)) {
            bootClientMode(msg);
            return;
        }

        getLogger().info("Booting embedded Spring context...");
        long start = System.currentTimeMillis();
        try {
            springContext = new SpringApplicationBuilder(WardenSpringApp.class)
                    .resourceLoader(new DefaultResourceLoader(getClassLoader()))
                    .bannerMode(Banner.Mode.OFF)
                    .initializers(context -> {
                        if (context instanceof GenericApplicationContext gac) {
                            gac.getBeanFactory().registerSingleton("platformBridge", new SpigotBridge(this, audiences));
                        }
                    })
                    .run();
            long tookMs = System.currentTimeMillis() - start;
            getLogger().info("Spring context booted in " + tookMs + "ms.");
        } catch (Throwable t) {
            getLogger().severe("Failed to boot embedded Spring context: " + t);
            t.printStackTrace();
            return;
        }

        PunishmentService punishmentService = springContext.getBean(PunishmentService.class);
        PlayerTrackingService playerTracking = springContext.getBean(PlayerTrackingService.class);
        IpHashingService ipHashing = springContext.getBean(IpHashingService.class);
        PunishmentTemplateService templates = springContext.getBean(PunishmentTemplateService.class);
        LangService lang = springContext.getBean(LangService.class);
        PlayerRepository playerRepository = springContext.getBean(PlayerRepository.class);
        MojangApiService mojangApi = springContext.getBean(MojangApiService.class);

        PunishmentCommands.register(this, punishmentService, templates, lang, playerRepository, mojangApi, msg);

        getServer().getPluginManager().registerEvents(new BanGateListener(punishmentService, ipHashing, lang), this);
        getServer().getPluginManager().registerEvents(new MuteGateListener(punishmentService, lang, audiences), this);
        getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(punishmentService, lang, audiences), this);
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(playerTracking), this);
    }

    /**
     * No local core at all - never boots Spring/JPA/SQLite. Just enough to talk to a proxy's
     * core over REST/WS, same shape as warden-paper's own CLIENT mode.
     */
    private void bootClientMode(SpigotMsg msg) {
        var handshake = CoreHandshakeListener.readCached(getDataFolder());
        if (handshake.isEmpty()) {
            getLogger().severe("warden.mode is 'client' but no proxy handshake is cached yet - "
                    + "join once through the proxy first (so it can announce itself to this "
                    + "server), or set warden.mode back to 'host'.");
            return;
        }
        CoreHandshake h = handshake.get();
        getLogger().info("CLIENT mode - connecting to Core at " + h.coreUrl() + "...");

        LangService lang = new LangService();
        CoreConfig rawConfig = new CoreConfig();
        try {
            rawConfig.getSecurity().setIpSalt(ConfigBootstrap.readIpSalt(configFile));
        } catch (Exception e) {
            getLogger().severe("Could not read warden.security.ip-salt from config.yml: " + e);
            return;
        }
        IpHashingService ipHashing = new IpHashingService(rawConfig);
        MojangApiService mojangApi = new MojangApiService();

        RemotePunishmentCache cache = new RemotePunishmentCache();
        WriteJournal journal = new WriteJournal(getDataFolder(), org.slf4j.LoggerFactory.getLogger(WardenSpigotPlugin.class));
        remoteClient = new RemoteCoreClient(h.coreUrl(), h.nodeToken(), cache, journal, org.slf4j.LoggerFactory.getLogger(RemoteCoreClient.class));
        remoteClient.start();

        getServer().getPluginManager().registerEvents(new BanGateListener(cache, ipHashing, lang), this);
        getServer().getPluginManager().registerEvents(new MuteGateListener(cache, lang, audiences), this);
        getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(cache, lang, audiences), this);
        RemotePunishmentCommands.register(this, remoteClient, cache, lang, mojangApi, msg);
    }

    @Override
    public void onDisable() {
        if (springContext != null && springContext.isActive()) {
            getLogger().info("Shutting down embedded Spring context...");
            springContext.close();
        }
        if (remoteClient != null) {
            remoteClient.stop();
        }
        if (audiences != null) {
            audiences.close();
        }
    }
}
