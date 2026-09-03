package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.bridge.PaperBridge;
import me.psikuvit.betterWarden.command.InfoCommands;
import me.psikuvit.betterWarden.command.PunishmentCommands;
import me.psikuvit.betterWarden.command.WardenAdminCommands;
import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.service.AltDetectionService;
import me.psikuvit.betterWarden.core.service.EscalationService;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import me.psikuvit.betterWarden.core.service.StaffNoteService;
import me.psikuvit.betterWarden.listener.BanGateListener;
import me.psikuvit.betterWarden.listener.MuteCommandBlockListener;
import me.psikuvit.betterWarden.listener.MuteGateListener;
import me.psikuvit.betterWarden.listener.PlayerTrackingListener;
import me.psikuvit.betterWarden.listener.SessionListener;
import me.psikuvit.betterWarden.scheduler.PaperScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;

public final class BetterWarden extends JavaPlugin {

    private ConfigurableApplicationContext springContext;
    private File configFile;

    @Override
    public void onEnable() {
        // Needed so Spring's autoconfiguration scanning can see the plugin jar's resources.
        Thread.currentThread().setContextClassLoader(getClassLoader());

        try {
            configFile = ConfigBootstrap.ensureConfigFile(getDataFolder(),
                    () -> getResource("default-config.yml"));
            ConfigBootstrap.ensureIpSalt(configFile);
            ConfigBootstrap.applyToSystemProperties(configFile, getDataFolder());
        } catch (Exception e) {
            getLogger().severe("Could not load config.yml: " + e);
            getServer().getPluginManager().disablePlugin(this);
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
                            gac.getBeanFactory().registerSingleton("platformBridge",
                                    new PaperBridge(new PaperScheduler(this)));
                        }
                    })
                    .run();
            long tookMs = System.currentTimeMillis() - start;
            getLogger().info("Spring context booted in " + tookMs + "ms. Try http://localhost:8095/health");
        } catch (Throwable t) {
            getLogger().severe("Failed to boot embedded Spring context: " + t);
            t.printStackTrace();
            return;
        }

        PunishmentService punishmentService = springContext.getBean(PunishmentService.class);
        PlayerTrackingService playerTracking = springContext.getBean(PlayerTrackingService.class);
        StaffNoteService staffNotes = springContext.getBean(StaffNoteService.class);
        IpHashingService ipHashing = springContext.getBean(IpHashingService.class);
        PunishmentTemplateService templates = springContext.getBean(PunishmentTemplateService.class);
        AltDetectionService altDetection = springContext.getBean(AltDetectionService.class);
        CoreConfig coreConfig = springContext.getBean(CoreConfig.class);
        EscalationService escalationService = springContext.getBean(EscalationService.class);

        PunishmentCommands.register(this, punishmentService, templates);
        InfoCommands.register(this, punishmentService, staffNotes, altDetection, playerTracking);
        WardenAdminCommands.register(this, templates, escalationService, coreConfig, configFile);

        getServer().getPluginManager().registerEvents(new BanGateListener(punishmentService, ipHashing), this);
        getServer().getPluginManager().registerEvents(new MuteGateListener(punishmentService), this);
        getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(punishmentService), this);
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(playerTracking), this);
        getServer().getPluginManager().registerEvents(new SessionListener(playerTracking), this);
    }

    @Override
    public void onDisable() {
        if (springContext != null && springContext.isActive()) {
            getLogger().info("Shutting down embedded Spring context...");
            springContext.close();
        }
    }
}
