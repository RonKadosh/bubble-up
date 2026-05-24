package com.ronkadosh.studybuddy.common.file;

import java.time.Instant;

public record FileMetadata(
        String fileId,
        String originalName,
        String contentType,
        long sizeBytes,
        FileAccessPolicy accessPolicy,
        Instant uploadedAt
) {}
