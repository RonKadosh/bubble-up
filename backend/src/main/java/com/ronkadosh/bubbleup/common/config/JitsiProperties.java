package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jitsi")
public record JitsiProperties(
        String appId,
        String kid,
        String privateKeyPem,
        String serverUrl,
        Duration tokenTtl
) {
    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && kid != null && !kid.isBlank()
                && privateKeyPem != null && !privateKeyPem.isBlank();
    }
}
