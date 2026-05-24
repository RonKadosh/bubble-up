package com.ronkadosh.studybuddy.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agents")
public record AgentProperties(
        boolean enabled
) {}
