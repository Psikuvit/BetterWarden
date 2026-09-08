package me.psikuvit.betterWarden;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.psikuvit.betterWarden.bridge.VelocityBridge;
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
import org.slf4j.Logger;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** Boots either HOST (embeds the core, Stage 3) or CLIENT (talks to an external core over REST/WS, Stage 4) per warden.mode. */
@Plugin(id = "betterwarden", name = "BetterWarden", version = "1.0",
        description = "Moderation & staff-ops platform - Velocity proxy")
public final class WardenVelocityPlugin {

    private static final String VERSION = "1.0";

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigurableApplicationContext springContext;
    private RemoteCoreClient remoteClient;
    /** Set only in CLIENT mode - what onServerPostConnect relays to backends instead of this proxy's own (nonexistent) embedded core. */
    private CoreHandshake clientHandshakeToRelay;

    @Inject
    public WardenVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Same reason as warden-bukkit: Spring's autoconfiguration scanning reads the
        // context classloader, and Velocity's plugin classloader is never the default one.
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        server.getChannelRegistrar().register(
                MinecraftChannelIdentifier.create(CoreHandshake.CHANNEL_NAMESPACE, CoreHandshake.CHANNEL_NAME));

        File dataFolder = dataDirectory.toFile();
        File configFile;
        String mode;
        try {
            configFile = ConfigBootstrap.ensureConfigFile(dataFolder,
                    () -> getClass().getClassLoader().getResourceAsStream("default-config.yml"));
            ConfigBootstrap.ensureSecuritySecrets(configFile);
            ConfigBootstrap.applyToSystemProperties(configFile, dataFolder);
            mode = ConfigBootstrap.readMode(configFile);
        } catch (Exception e) {
            logger.error("Could not load config.yml - BetterWarden will not start", e);
            return;
        }

        if ("CLIENT".equals(mode)) {
            bootClientMode(configFile, dataFolder);
            return;
        }

        logger.info("Booting embedded Spring context...");
        long start = System.currentTimeMillis();
        try {
            springContext = new SpringApplicationBuilder(WardenSpringApp.class)
                    .resourceLoader(new DefaultResourceLoader(getClass().getClassLoader()))
                    .bannerMode(Banner.Mode.OFF)
                    .initializers(context -> {
                        if (context instanceof GenericApplicationContext gac) {
                            gac.getBeanFactory().registerSingleton("platformBridge", new VelocityBridge(server));
                        }
                    })
                    .run();
            long tookMs = System.currentTimeMillis() - start;
            logger.info("Spring context booted in {}ms.", tookMs);
        } catch (Throwable t) {
            // Most likely cause: panel.port already bound by another process/instance.
            logger.error("Failed to boot embedded Spring context - check panel.port isn't already in use", t);
            return;
        }

        PunishmentService punishmentService = springContext.getBean(PunishmentService.class);
        LangService lang = springContext.getBean(LangService.class);
        CoreConfig config = springContext.getBean(CoreConfig.class);
        NodeWebSocketHandler nodeHub = springContext.getBean(NodeWebSocketHandler.class);
        SetupCodeService setupCodeService = springContext.getBean(SetupCodeService.class);
        PanelUserRepository panelUsers = springContext.getBean(PanelUserRepository.class);

        GlobalPunishmentCommands.register(this, server, springContext.getBean(PlayerRepository.class), punishmentService, lang);
        WardenProxyCommands.register(this, server, lang,
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

        server.getEventManager().register(this, new ProxyLoginGateListener(
                punishmentService, springContext.getBean(IpHashingService.class), config, lang, logger));
    }

    /**
     * No local core at all - never boots Spring/JPA/SQLite/Tomcat. Mirrors warden-bukkit's CLIENT
     * mode, but this proxy has no upstream handshake to read its core URL from (it IS the top of
     * the topology when pointed at a standalone core) - warden.core.url in its own config.yml
     * instead. Still relays a handshake to backends on join, same as HOST mode, so a Paper backend
     * behind this proxy doesn't need to know or care whether the core is embedded or standalone.
     */
    private void bootClientMode(File configFile, File dataFolder) {
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
            logger.error("Could not read config.yml for CLIENT mode", e);
            return;
        }
        if (coreUrl.isBlank()) {
            logger.error("warden.mode is 'client' but warden.core.url is not set - "
                    + "point it at your standalone core (e.g. http://localhost:8095) and restart.");
            return;
        }
        logger.info("CLIENT mode - connecting to Core at {}...", coreUrl);

        LangService lang = new LangService();
        CoreConfig rawConfig = new CoreConfig();
        rawConfig.getSecurity().setIpSalt(ipSalt);
        rawConfig.getLoginGate().setFailOpen(failOpen);
        IpHashingService ipHashing = new IpHashingService(rawConfig);

        RemotePunishmentCache cache = new RemotePunishmentCache();
        WriteJournal journal = new WriteJournal(dataFolder, logger);
        remoteClient = new RemoteCoreClient(coreUrl, nodeToken, cache, journal, logger);
        remoteClient.start();

        RemoteGlobalPunishmentCommands.register(this, server, remoteClient, cache, lang);
        // No local SetupCodeService in CLIENT mode - setup happens directly against whatever
        // core this proxy points at, not through this process.
        WardenProxyCommands.register(this, server, lang, () -> coreUrl, remoteClient::nodeCount,
                () -> "<gray>Not available in CLIENT mode - use the setup wizard on the core this proxy connects to.");
        server.getEventManager().register(this, new ProxyLoginGateListener(cache, ipHashing, rawConfig, lang, logger));

        clientHandshakeToRelay = new CoreHandshake(coreUrl, nodeToken, VERSION, CoreHandshake.TIER_STANDALONE);
    }

    /** Announces the active core (embedded or, in CLIENT mode, the external one this proxy itself talks to) to whichever backend a player just connected to. */
    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
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

        ServerConnection connection = event.getPlayer().getCurrentServer().orElse(null);
        if (connection == null) {
            return;
        }
        connection.sendPluginMessage(
                MinecraftChannelIdentifier.create(CoreHandshake.CHANNEL_NAMESPACE, CoreHandshake.CHANNEL_NAME),
                handshake.toBytes());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (springContext != null && springContext.isActive()) {
            logger.info("Shutting down embedded Spring context...");
            springContext.close();
        }
        if (remoteClient != null) {
            remoteClient.stop();
        }
    }
}
