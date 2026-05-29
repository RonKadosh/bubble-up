package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import com.ronkadosh.bubbleup.catalog.model.TermKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mutation commands accepted by {@link com.ronkadosh.bubbleup.catalog.internal.CatalogAdminInternalService}.
 * Update commands are PATCH semantics: any null field is left unchanged.
 */
public final class CatalogCommands {
    private CatalogCommands() {}

    public record CreateUniversity(String name, String shortCode, String country) {}

    public record UpdateUniversity(String name, String shortCode, String country) {}

    public record CreateDepartment(UUID universityId, String name, String shortCode) {}

    public record UpdateDepartment(String name, String shortCode) {}

    public record CreateTerm(
            UUID universityId,
            String code,
            String name,
            TermKind kind,
            int academicYear,
            LocalDate startsOn,
            LocalDate endsOn
    ) {}

    public record UpdateTerm(
            String code,
            String name,
            TermKind kind,
            Integer academicYear,
            LocalDate startsOn,
            LocalDate endsOn
    ) {}

    public record CreateCourse(
            UUID universityId,
            String code,
            String name,
            BigDecimal creditPoints,
            String description
    ) {}

    public record UpdateCourse(
            String code,
            String name,
            BigDecimal creditPoints,
            String description
    ) {}
}
