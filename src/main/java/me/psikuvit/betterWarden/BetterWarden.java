package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.core.WardenSpringApp;
import org.bukkit.plugin.java.JavaPlugin;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

public final class BetterWarden extends JavaPlugin {

    private ConfigurableApplicationContext springContext;

    @Override
    public void onEnable() {
        // Needed so Spring's autoconfiguration scanning can see the plugin jar's resources.
        Thread.currentThread().setContextClassLoader(getClassLoader());

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
