package com.ronkadosh.bubbleup.groups.internal.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A recently uploaded group file. Drives the "Bubble activity" feed section
 * ("lecture3.pdf uploaded to Calc Bubble").
 */
public record GroupFileActivityItem(
        UUID fileId,
        UUID groupId,
        UUID uploaderId,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {}
