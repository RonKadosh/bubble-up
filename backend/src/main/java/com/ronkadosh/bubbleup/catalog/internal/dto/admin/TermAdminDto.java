package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import com.ronkadosh.bubbleup.catalog.model.TermKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TermAdminDto(
        UUID id,
        UUID universityId,
        String code,
        String name,
        TermKind kind,
        int academicYear,
        LocalDate startsOn,
        LocalDate endsOn,
        Instant createdAt
) {}
