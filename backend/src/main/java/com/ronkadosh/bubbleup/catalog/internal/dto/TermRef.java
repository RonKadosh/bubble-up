package com.ronkadosh.bubbleup.catalog.internal.dto;

import com.ronkadosh.bubbleup.catalog.model.TermKind;

import java.time.LocalDate;
import java.util.UUID;

public record TermRef(
        UUID id,
        UUID universityId,
        String code,
        String name,
        TermKind kind,
        int academicYear,
        LocalDate startsOn,
        LocalDate endsOn
) {}
