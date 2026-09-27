package com.toine.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PersistenceConfig {

    /**
     * Writes laps to the database off the UDP handler thread. A single thread keeps the writes in the
     * order the laps happened (save lap 3, retract lap 3, save lap 3 again, correct its timing), and
     * a lap only completes every minute or so, so one thread is plenty. The bounded queue stops a
     * database outage from exhausting memory; waiting on shutdown lets queued laps still be saved.
     */
    @Bean
    public ThreadPoolTaskExecutor lapWriterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("lap-writer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }
}
