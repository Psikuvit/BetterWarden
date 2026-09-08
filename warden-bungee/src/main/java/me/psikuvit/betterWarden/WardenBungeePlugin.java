package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.bridge.BungeeBridge;
import me.psikuvit.betterWarden.command.BungeeMsg;
import me.psikuvit.betterWarden.command.GlobalPunishmentCommands;
import me.psikuvit.betterWarden.command.RemoteGlobalPunishmentCommands;
import me.psikuvit.betterWarden.command.WardenProxyCommands;
import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.client.RemoteCoreClient;
import me.psikuvit.betterWarden.core.client.RemotePunishmentCache;
import me.psikuvit.betterWarden.core.client.WriteJournal;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.network.CoreHandshake;
import me.psikuvit.betterWarden.core.panel.setup.SetupCodeService;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.ws.NodeWebSocketHandler;
import me.psikuvit.betterWarden.listener.ProxyLoginGateListener;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.util.Optional;

/**
 * Boots either HOST (embeds the core) or CLIENT (talks to an external core over REST/WS) per
 * warden.mode - same dual-mode shape as WardenVelocityPlugin, on BungeeCord/Waterfall's own
 * completely different plugin API (net.md_5.bungee.*, not API-compatible with Velocity at all).
 */
public final class WardenBungeePlugin extends Plugin implements Listener {

    private static final String VERSION = "1.0";

    private ConfigurableApplicationContext springContext;
    private RemoteCoreClient remoteClient;
    private BungeeAudiences audiences;
    /** Set only in CLIENT mode - what onServerConnected relays to backends instead of this proxy's own (nonexistent) embedded core. */
    private CoreHandshake clientHandshakeToRelay;

    @Override
    public void onEnable() {
        // Same reason as warden-bukkit/warden-velocity: Spring's autoconfiguration scanning reads
        // the context classloader, and Bungee's plugin classloader is never the default one.
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        audiences = BungeeAudiences.create(this);
        BungeeMsg msg = new BungeeMsg(audiences);
        ProxyServer server = getProxy();
        server.registerChannel(CoreHandshake.CHANNEL_ID);
        server.getPluginManager().registerListener(this, this);

        File dataFolder = getDataFolder();
        File configFile;
        String mode;
        try {
            configFile = ConfigBootstrap.ensureConfigFile(dataFolder, () -> getResourceAsStream("default-config.yml"));
            ConfigBootstrap.ensureSecuritySecrets(configFile);
            ConfigBootstrap.applyToSystemProperties(configFile, dataFolder);
            mode = ConfigBootstrap.readMode(configFile);
        } catch (Exception e) {
            getLogger().severe("Could not load config.yml - BetterWarden will not start: " + e);
            return;
        }

        if ("CLIENT".equals(mode)) {
            bootClientMode(configFile, dataFolder, server, msg);
            return;
        }

        getLogger().info("Booting embedded Spring context...");
        long start = System.currentTimeMillis();
        try {
            springContext = new SpringApplicationBuilder(WardenSpringApp.class)
                    .resourceLoader(new DefaultResourceLoader(getClass().getClassLoader()))
                    .bannerMode(Banner.Mode.OFF)
                    .initializers(context -> {
                        if (context instanceof GenericApplicationContext gac) {
                            gac.getBeanFactory().registerSingleton("platformBridge", new BungeeBridge(server, audiences));
                        }
                    })
                    .run();
            long tookMs = System.currentTimeMillis() - start;
            getLogger().info("Spring context booted in " + tookMs + "ms.");
        } catch (Throwable t) {
            // Most likely cause: panel.port already bound by another process/instance.
            getLogger().severe("Failed to boot embedded Spring context - check panel.port isn't already in use: " + t);
            return;
        }

        PunishmentService punishmentService = springContext.getBean(PunishmentService.class);
        LangService lang = springContext.getBean(LangService.class);
        CoreConfig config = springContext.getBean(CoreConfig.class);
        NodeWebSocketHandler nodeHub = springContext.getBean(NodeWebSocketHandler.class);
        SetupCodeService setupCodeService = springContext.getBean(SetupCodeService.class);
        PanelUserRepository panelUsers = springContext.getBean(PanelUserRepository.class);

        GlobalPunishmentCommands.register(this, server, springContext.getBean(PlayerRepository.class), punishmentService, lang, msg);
        WardenProxyCommands.register(this, server, lang, msg,
                () -> "http://" + config.getNode().getAdvertiseHost() + ":" + config.getPanel().getPort(),
                () -> Optional.of(nodeHub.connectedCount()),
                () -> {
                    if (panelUsers.count() > 0) {
                        return lang.get("admin.setup-already-done");
                    }
                    return lang.get("admin.setup-code-header") + "\n"
                            + lang.get("admin.setup-code-url", config.getPanel().getPort()) + "\n"
                            + lang.get("admin.setup-code-value", setupCodeService.generate());
                });

        server.getPluginManager().registerListener(this, new ProxyLoginGateListener(this, server,
                punishmentService, springContext.getBean(IpHashingService.class), config, lang,
                LoggerFactory.getLogger(WardenBungeePlugin.class)));
    }

    /**
     * No local core at all - never boots Spring/JPA/SQLite/Netty-HTTP. Mirrors warden-bukkit's and
     * warden-velocity's CLIENT mode: this proxy reads warden.core.url from its own config.yml
     * (it IS the top of the topology when pointed at a standalone core) and still relays a
     * handshake to backends on join, same as HOST mode, so a Paper backend behind it doesn't
     * need to know or care whether the core is embedded or standalone.
     */
    private void bootClientMode(File configFile, File dataFolder, ProxyServer server, BungeeMsg msg) {
        String coreUrl;
        String nodeToken;
        boolean failOpen;
        String ipSalt;
        try {
            coreUrl = ConfigBootstrap.readCoreUrl(configFile);
            nodeToken = ConfigBootstrap.readNodeToken(configFile);
            failOpen = ConfigBootstrap.readLoginGateFailOpen(configFile);
            ipSalt = ConfigBootstrap.readIpSalt(configFile);
        } catch (Exception e) {
            getLogger().severe("Could not read config.yml for CLIENT mode: " + e);
            return;
        }
        if (coreUrl.isBlank()) {
            getLogger().severe("warden.mode is 'client' but warden.core.url is not set - "
                    + "point it at your standalone core (e.g. http://localhost:8095) and restart.");
            return;
        }
        getLogger().info("CLIENT mode - connecting to Core at " + coreUrl + "...");

        LangService lang = new LangService();
        CoreConfig rawConfig = new CoreConfig();
        rawConfig.getSecurity().setIpSalt(ipSalt);
        rawConfig.getLoginGate().setFailOpen(failOpen);
        IpHashingService ipHashing = new IpHashingService(rawConfig);

        RemotePunishmentCache cache = new RemotePunishmentCache();
        WriteJournal journal = new WriteJournal(dataFolder, LoggerFactory.getLogger(WardenBungeePlugin.class));
        remoteClient = new RemoteCoreClient(coreUrl, nodeToken, cache, journal, LoggerFactory.getLogger(RemoteCoreClient.class));
        remoteClient.start();

        RemoteGlobalPunishmentCommands.register(this, server, remoteClient, cache, lang, msg);
        // No local SetupCodeService in CLIENT mode - setup happens directly against whatever
        // core this proxy points at, not through this process.
        WardenProxyCommands.register(this, server, lang, msg, () -> coreUrl, remoteClient::nodeCount,
                () -> "<gray>Not available in CLIENT mode - use the setup wizard on the core this proxy connects to.");
        server.getPluginManager().registerListener(this, new ProxyLoginGateListener(this, server,
                cache, ipHashing, rawConfig, lang, LoggerFactory.getLogger(WardenBungeePlugin.class)));

        clientHandshakeToRelay = new CoreHandshake(coreUrl, nodeToken, VERSION, CoreHandshake.TIER_STANDALONE);
    }

    /** Announces the active core (embedded or, in CLIENT mode, the external one this proxy itself talks to) to whichever backend a player just connected to. */
    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        CoreHandshake handshake;
        if (springContext != null && springContext.isActive()) {
            CoreConfig config = springContext.getBean(CoreConfig.class);
            String coreUrl = "http://" + config.getNode().getAdvertiseHost() + ":" + config.getPanel().getPort();
            handshake = new CoreHandshake(coreUrl, config.getSecurity().getNodeToken(), VERSION, CoreHandshake.TIER_EMBEDDED);
        } else if (clientHandshakeToRelay != null) {
            handshake = clientHandshakeToRelay;
        } else {
            return;
        }
        Server backend = event.getServer();
        if (backend != null) {
            backend.sendData(CoreHandshake.CHANNEL_ID, handshake.toBytes());
        }
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
