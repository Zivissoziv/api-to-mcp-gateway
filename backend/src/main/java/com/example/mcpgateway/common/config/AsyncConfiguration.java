package com.example.mcpgateway.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Bean(name = "gatewayCallRecorderExecutor")
    public Executor gatewayCallRecorderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("gateway-call-recorder-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("Gateway call record task discarded: active={}, queued={}",
                        pool.getActiveCount(), pool.getQueue().size()));
        executor.initialize();
        return executor;
    }
}
