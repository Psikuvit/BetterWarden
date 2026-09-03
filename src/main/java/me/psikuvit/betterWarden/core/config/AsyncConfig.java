package me.psikuvit.betterWarden.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** Backs every {@code @Async} service method — see docs/spec/00-OVERVIEW.txt §8. */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "wardenExecutor")
    public Executor wardenExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("warden-async-");
        executor.initialize();
        return executor;
    }
}
