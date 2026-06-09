package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.help.ai")
public record HelpAiProperties(
        boolean enabled,
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Duration timeout
) {
    public boolean configured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
