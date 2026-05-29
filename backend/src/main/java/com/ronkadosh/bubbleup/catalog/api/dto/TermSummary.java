package com.ronkadosh.bubbleup.catalog.api.dto;

import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.catalog.model.Term;
import com.ronkadosh.bubbleup.catalog.model.TermKind;

import java.time.LocalDate;
import java.util.UUID;

public record TermSummary(
        UUID id,
        UUID universityId,
        String code,
        String name,
        TermKind kind,
        int academicYear,
        LocalDate startsOn,
        LocalDate endsOn
) {
    public static TermSummary from(Term t) {
        return new TermSummary(
                t.getId(), t.getUniversityId(), t.getCode(), t.getName(),
                t.getKind(), t.getAcademicYear(), t.getStartsOn(), t.getEndsOn());
    }

    public static TermSummary from(TermRef t) {
        return new TermSummary(
                t.id(), t.universityId(), t.code(), t.name(),
                t.kind(), t.academicYear(), t.startsOn(), t.endsOn());
    }
}
