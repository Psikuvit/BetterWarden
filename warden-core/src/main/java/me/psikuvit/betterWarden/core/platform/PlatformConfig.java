package me.psikuvit.betterWarden.core.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers {@link NoOpBridge} only if nothing else provided a {@link PlatformBridge} bean. */
@Configuration
public class PlatformConfig {

    @Bean
    @ConditionalOnMissingBean(PlatformBridge.class)
    public PlatformBridge noOpBridge() {
        return new NoOpBridge();
    }
}
