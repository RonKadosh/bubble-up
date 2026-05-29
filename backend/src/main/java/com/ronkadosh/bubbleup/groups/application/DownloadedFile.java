package com.ronkadosh.bubbleup.groups.application;

public record DownloadedFile(
        byte[] bytes,
        String contentType,
        String originalName,
        long sizeBytes
) {}
