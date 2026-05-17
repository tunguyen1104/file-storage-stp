package com.filestorage.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class SseConfig {

    @Bean(name = "sseScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService sseScheduler() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "sse-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }
}
