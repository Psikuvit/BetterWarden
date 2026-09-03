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
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import me.psikuvit.betterWarden.core.service.StaffNoteService;
import me.psikuvit.betterWarden.hook.LuckPermsHook;
import me.psikuvit.betterWarden.hook.PlaceholderApiHook;
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
        LangService lang = springContext.getBean(LangService.class);

        PunishmentCommands.register(this, punishmentService, templates, lang);
        InfoCommands.register(this, punishmentService, staffNotes, altDetection, playerTracking, lang);
        WardenAdminCommands.register(this, templates, escalationService, coreConfig, configFile, lang);

        getServer().getPluginManager().registerEvents(new BanGateListener(punishmentService, ipHashing, lang), this);
        getServer().getPluginManager().registerEvents(new MuteGateListener(punishmentService, lang), this);
        getServer().getPluginManager().registerEvents(new MuteCommandBlockListener(punishmentService, lang), this);
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(playerTracking), this);
        getServer().getPluginManager().registerEvents(new SessionListener(playerTracking), this);

        registerHooks(punishmentService);
    }

    /** Soft-depends: each hook type is only ever loaded by the JVM once its plugin is confirmed present. */
    private void registerHooks(PunishmentService punishmentService) {
        LuckPermsHook luckPermsHook = null;
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            luckPermsHook = new LuckPermsHook();
            getLogger().info("LuckPerms detected - staff rank lookups enabled.");
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderApiHook(punishmentService, luckPermsHook).register();
            getLogger().info("PlaceholderAPI detected - %warden_...% placeholders registered.");
        }
    }

    @Override
    public void onDisable() {
        if (springContext != null && springContext.isActive()) {
            getLogger().info("Shutting down embedded Spring context...");
            springContext.close();
        }
    }
}
