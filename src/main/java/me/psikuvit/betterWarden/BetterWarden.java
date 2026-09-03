package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.File;

public final class BetterWarden extends JavaPlugin {

    private ConfigurableApplicationContext springContext;

    @Override
    public void onEnable() {
        // Needed so Spring's autoconfiguration scanning can see the plugin jar's resources.
        Thread.currentThread().setContextClassLoader(getClassLoader());

        try {
            File configFile = ConfigBootstrap.ensureConfigFile(getDataFolder(),
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
                    .run();
            long tookMs = System.currentTimeMillis() - start;
            getLogger().info("Spring context booted in " + tookMs + "ms.");
        } catch (Throwable t) {
            getLogger().severe("Failed to boot embedded Spring context: " + t);
            t.printStackTrace();
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
