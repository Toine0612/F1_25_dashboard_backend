package com.toine.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "ioDatabaseExecutor")
    public Executor ioDatabaseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core threads handle normal load
        executor.setCorePoolSize(10);
        // Max threads handle sudden bursts of lap completions
        executor.setMaxPoolSize(50);
        // How many lap flushes can wait in line before we reject them
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("DB-Flush-");

        // Critical: Abort policy prevents memory exhaustion if DB goes offline
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}