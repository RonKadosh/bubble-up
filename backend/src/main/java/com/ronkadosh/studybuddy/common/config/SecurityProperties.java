package com.ronkadosh.studybuddy.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        long jwtAccessExpirationMs,
        long refreshTokenExpirationMs
) {}
