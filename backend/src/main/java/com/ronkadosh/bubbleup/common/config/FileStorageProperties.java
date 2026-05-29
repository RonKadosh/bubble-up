package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(
        String type,
        String localPath
) {}
