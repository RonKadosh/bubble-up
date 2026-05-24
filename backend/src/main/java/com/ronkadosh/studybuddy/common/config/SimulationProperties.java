package com.ronkadosh.studybuddy.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.simulation")
public record SimulationProperties(
        boolean enabled
) {}
