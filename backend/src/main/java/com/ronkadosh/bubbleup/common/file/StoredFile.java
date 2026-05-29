package com.ronkadosh.bubbleup.common.file;

import java.time.Instant;

public record StoredFile(
        String fileId,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {}
