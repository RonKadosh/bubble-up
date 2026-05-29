package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agents")
public record AgentProperties(
        boolean enabled
) {}
