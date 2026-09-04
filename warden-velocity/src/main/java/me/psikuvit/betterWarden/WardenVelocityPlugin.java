package me.psikuvit.betterWarden;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.bridge.VelocityBridge;
import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import org.slf4j.Logger;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;
import java.nio.file.Path;

/** Stage 3 risk spike: same embedded-Spring approach as warden-paper (Stage 0), done for Velocity. HOST mode only for now - no handshake/CLIENT mode yet. */
@Plugin(id = "betterwarden", name = "BetterWarden", version = "1.0",
        description = "Moderation & staff-ops platform - Velocity proxy")
public final class WardenVelocityPlugin {

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

        File dataFolder = dataDirectory.toFile();
        File configFile;
        try {
            configFile = ConfigBootstrap.ensureConfigFile(dataFolder,
                    () -> getClass().getClassLoader().getResourceAsStream("default-config.yml"));
            ConfigBootstrap.ensureIpSalt(configFile);
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
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (springContext != null && springContext.isActive()) {
            logger.info("Shutting down embedded Spring context...");
            springContext.close();
        }
    }
}
