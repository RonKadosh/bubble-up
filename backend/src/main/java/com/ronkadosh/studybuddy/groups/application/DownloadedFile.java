package com.ronkadosh.studybuddy.groups.application;

public record DownloadedFile(
        byte[] bytes,
        String contentType,
        String originalName,
        long sizeBytes
) {}
