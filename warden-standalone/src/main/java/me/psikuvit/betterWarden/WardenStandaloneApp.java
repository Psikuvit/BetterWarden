package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.core.WardenSpringApp;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import org.springframework.boot.SpringApplication;

import java.io.File;

/**
 * Stage 4: the standalone core (Docker/Network tier). No PlatformBridge is registered here -
 * NoOpBridge (warden-core's PlatformConfig, @ConditionalOnMissingBean) covers it: this process
 * only serves REST/WS to CLIENT nodes, it never kicks/messages a player directly itself.
 */
public final class WardenStandaloneApp {

    public static void main(String[] args) throws Exception {
        File dataFolder = new File(System.getenv().getOrDefault("WARDEN_HOME", "/data"));
        File configFile = ConfigBootstrap.ensureConfigFile(dataFolder,
                () -> WardenStandaloneApp.class.getResourceAsStream("/default-config.yml"));
        ConfigBootstrap.ensureSecuritySecrets(configFile);
        ConfigBootstrap.applyToSystemProperties(configFile, dataFolder);

        SpringApplication.run(WardenSpringApp.class, args);
    }
}
