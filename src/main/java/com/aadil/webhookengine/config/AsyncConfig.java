package com.aadil.webhookengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables @Async and @Scheduled for the application.
 *
 * Why a custom executor instead of the default SimpleAsyncTaskExecutor?
 *   - SimpleAsyncTaskExecutor spawns a new thread per task — no pooling.
 *   - Under a fan-out of 10 subscribers, that's 10 unbounded threads per emit.
 *   - ThreadPoolTaskExecutor gives us a bounded, reusable pool with a queue.
 *
 * Sizing rationale (free-tier EC2 t2.micro = 1 vCPU, 1 GB RAM):
 *   - corePoolSize 5  → 5 threads always alive, handles normal load
 *   - maxPoolSize  20 → burst headroom for large fan-outs
 *   - queueCapacity 100 → tasks queue here before new threads are spawned
 *   - keepAlive 60s → idle threads above core are reaped after 60s
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "webhookTaskExecutor")
    public Executor webhookTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("webhook-delivery-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
