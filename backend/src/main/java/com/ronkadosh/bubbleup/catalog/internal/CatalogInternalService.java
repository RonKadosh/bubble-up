package com.ronkadosh.bubbleup.catalog.internal;

import com.ronkadosh.bubbleup.catalog.internal.dto.CourseRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.DepartmentRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.UniversityRef;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module read surface for the academic catalog. Other modules (currently
 * {@code groups/}, {@code matching/}, {@code auth/}) call into this to validate
 * a {@code courseId} / {@code offeringId}, look up the current term for a
 * university, or expand a department/university scope into a list of matching
 * offering IDs for filtering.
 *
 * Mutations are intentionally not exposed — the catalog is populated by the
 * seeder. Admin CRUD lives in a future iteration.
 */
public interface CatalogInternalService {

    // ─── Universities / Departments ───────────────────────────────────────────
    boolean universityExists(UUID universityId);

    boolean departmentExists(UUID departmentId);

    /**
     * Name / shortCode for a university. Used by user-profile responses to
     * denormalize the affiliation line server-side.
     */
    Optional<UniversityRef> getUniversity(UUID universityId);

    /**
     * Name + parent universityId for a department. Same purpose as
     * {@link #getUniversity(UUID)}.
     */
    Optional<DepartmentRef> getDepartment(UUID departmentId);

    // ─── Courses ──────────────────────────────────────────────────────────────
    boolean courseExists(UUID courseId);

    Optional<CourseRef> getCourseRef(UUID courseId);

    /** Departments a course is offered by. Empty if the course isn't linked to any. */
    List<UUID> departmentIdsForCourse(UUID courseId);

    /** All courses offered by a department (M:N via {@code course_departments}). */
    List<UUID> courseIdsForDepartment(UUID departmentId);

    /** All courses owned by a university. */
    List<UUID> courseIdsForUniversity(UUID universityId);

    // ─── Terms ────────────────────────────────────────────────────────────────

    /**
     * The current academic term for the given university.
     * <ul>
     *   <li>If a term's {@code [startsOn, endsOn]} window contains today, return it.</li>
     *   <li>If multiple match (overlap), prefer {@code kind != SUMMER}, then earliest {@code startsOn}.</li>
     *   <li>If none contain today, return the next upcoming term ({@code startsOn > today}).</li>
     *   <li>If still none, {@link Optional#empty()}.</li>
     * </ul>
     */
    Optional<TermRef> currentTermFor(UUID universityId);

    List<TermRef> termsFor(UUID universityId);

    boolean termExists(UUID termId);

    // ─── Offerings ────────────────────────────────────────────────────────────
    boolean offeringExists(UUID offeringId);

    Optional<OfferingRef> getOfferingRef(UUID offeringId);

    /**
     * Map of {@code offeringId → courseId} for a set of offering ids. Reads
     * {@code course_offerings.course_id} directly (no course/term joins), so
     * it's a single round-trip — use this when callers only need the course
     * link for a page of groups and don't want the full {@link OfferingRef}.
     */
    Map<UUID, UUID> getCourseIdsByOfferingIds(Collection<UUID> offeringIds);

    Optional<UUID> offeringIdForCourseAndTerm(UUID courseId, UUID termId);

    /** All offerings (any term) for the given courses. */
    List<UUID> offeringIdsForCourses(java.util.Collection<UUID> courseIds);

    /** All offerings for the given term across all courses. */
    List<UUID> offeringIdsForTerm(UUID termId);

    /** Offerings of any course offered by {@code departmentId} in {@code termId}. */
    List<UUID> offeringIdsForDepartmentAndTerm(UUID departmentId, UUID termId);

    List<UUID> offeringIdsForUniversityAndTerm(UUID universityId, UUID termId);

    // ─── Affiliation checks ───────────────────────────────────────────────────

    /** Used by user-profile validation: enforce that a chosen dept belongs to the chosen uni. */
    boolean departmentBelongsToUniversity(UUID departmentId, UUID universityId);
}
