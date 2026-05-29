package com.ronkadosh.bubbleup.matching.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class MatchingConfig {

    @Bean("matchingExecutor")
    public Executor matchingExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setThreadNamePrefix("matching-");
        exec.initialize();
        return exec;
    }
}
