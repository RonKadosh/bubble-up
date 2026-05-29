package com.ronkadosh.bubbleup.catalog.application.seed;

import com.ronkadosh.bubbleup.catalog.model.TermKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Declarative tree of the academic catalog to seed at boot. Each level is a record
 * so a different data source (Java code, YAML, JSON, admin import) can produce the
 * same shape without touching {@link CatalogSeeder}.
 *
 * The seeder is idempotent at every level — re-running the same tree never produces
 * duplicates, and growing the tree (new dept, new course, new term, new course-dept
 * link, new offering) only inserts the missing rows.
 */
public record CatalogSeedData(List<UniversitySeed> universities) {

    public static CatalogSeedData empty() {
        return new CatalogSeedData(List.of());
    }

    public record UniversitySeed(
            String shortCode,
            String name,
            String country,
            List<DepartmentSeed> departments,
            List<CourseSeed> courses,
            List<TermSeed> terms,
            List<OfferingSeed> offerings
    ) {}

    public record DepartmentSeed(
            String shortCode,
            String name
    ) {}

    /**
     * One course offered by a university. {@code departments} is the list of
     * department shortCodes this course is cross-listed in, plus which one is the
     * primary (home) department.
     */
    public record CourseSeed(
            String code,
            String name,
            BigDecimal creditPoints,
            String description,
            List<CourseDepartmentLinkSeed> departments
    ) {}

    public record CourseDepartmentLinkSeed(
            String departmentShortCode,
            boolean primary
    ) {}

    /**
     * One academic term offered by a university. Keyed on {@code (universityId, code)}.
     * {@code academicYear} is the year the academic year *starts* (e.g. 2025 covers
     * 2025-A, 2025-B, 2025-S of the 2025-2026 academic year).
     */
    public record TermSeed(
            String code,
            String name,
            TermKind kind,
            int academicYear,
            LocalDate startsOn,
            LocalDate endsOn
    ) {}

    /**
     * One {@code (course, term)} pair — i.e. "this course is offered in this term."
     * The seeder resolves {@code courseCode} and {@code termCode} against this
     * university's courses and terms.
     */
    public record OfferingSeed(
            String courseCode,
            String termCode
    ) {}
}
