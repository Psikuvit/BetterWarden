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
import me.psikuvit.betterWarden.command.WardenProxyCommands;
import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.network.CoreHandshake;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.listener.ProxyLoginGateListener;
import org.slf4j.Logger;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.nio.file.Path;

/** Stage 3 risk spike: same embedded-Spring approach as warden-paper (Stage 0), done for Velocity. HOST mode only - announces itself to backends on join, but there's no CLIENT mode on the receiving end yet. */
@Plugin(id = "betterwarden", name = "BetterWarden", version = "1.0",
        description = "Moderation & staff-ops platform - Velocity proxy")
public final class WardenVelocityPlugin {

    private static final String VERSION = "1.0";

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigurableApplicationContext springContext;

    @Inject
    public WardenVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Same reason as warden-paper: Spring's autoconfiguration scanning reads the
        // context classloader, and Velocity's plugin classloader is never the default one.
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        server.getChannelRegistrar().register(
                MinecraftChannelIdentifier.create(CoreHandshake.CHANNEL_NAMESPACE, CoreHandshake.CHANNEL_NAME));

        File dataFolder = dataDirectory.toFile();
        File configFile;
        try {
            configFile = ConfigBootstrap.ensureConfigFile(dataFolder,
                    () -> getClass().getClassLoader().getResourceAsStream("default-config.yml"));
            ConfigBootstrap.ensureSecuritySecrets(configFile);
            ConfigBootstrap.applyToSystemProperties(configFile, dataFolder);
        } catch (Exception e) {
            logger.error("Could not load config.yml - BetterWarden will not start", e);
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

        GlobalPunishmentCommands.register(server, springContext.getBean(PlayerRepository.class), punishmentService, lang);
        WardenProxyCommands.register(server, config, lang);

        server.getEventManager().register(this, new ProxyLoginGateListener(
                punishmentService, springContext.getBean(IpHashingService.class), config, lang, logger));
    }

    /** Announces this proxy's embedded core to whichever backend a player just connected to. */
    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (springContext == null || !springContext.isActive()) {
            return;
        }
        ServerConnection connection = event.getPlayer().getCurrentServer().orElse(null);
        if (connection == null) {
            return;
        }

        CoreConfig config = springContext.getBean(CoreConfig.class);
        String coreUrl = "http://" + config.getNode().getAdvertiseHost() + ":" + config.getPanel().getPort();
        CoreHandshake handshake = new CoreHandshake(coreUrl, config.getSecurity().getNodeToken(),
                VERSION, CoreHandshake.TIER_EMBEDDED);

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
    }
}
