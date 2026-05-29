package com.ronkadosh.bubbleup.catalog.internal;

import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CatalogCommands;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseAdminDetail;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseDepartmentLinkDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.DepartmentAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.OfferingAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.TermAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.UniversityAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only mutation + extended-read surface over the academic catalog. Separate
 * from the read-only {@link CatalogInternalService} because the read interface is
 * a stable contract depended on by {@code groups/}, {@code matching/}, and
 * {@code auth/}; admin growth shouldn't pollute those callers' API surface.
 *
 * <p>Throws typed {@code AppException}s via {@link com.ronkadosh.bubbleup.common.error.ErrorCode}:
 * <ul>
 *   <li>{@code *_NOT_FOUND} when an id is missing.</li>
 *   <li>{@code ADMIN_CATALOG_*_HAS_*} when deletion is blocked by a dependent row.</li>
 *   <li>{@code ADMIN_CATALOG_*_TAKEN} on unique-constraint conflicts.</li>
 *   <li>{@code ADMIN_CATALOG_TERM_DATES_INVALID} when {@code startsOn > endsOn}.</li>
 *   <li>{@code DEPARTMENT_NOT_IN_UNIVERSITY} when linking a course to a dept of a different uni.</li>
 * </ul>
 *
 * <p>Course/Offering deletion is unconditional here — the caller (admin module)
 * is responsible for the cross-module "are any groups using this?" check against
 * {@code GroupInternalService} before invoking delete.
 */
public interface CatalogAdminInternalService {

    // ─── Universities ─────────────────────────────────────────────────────────
    List<UniversityAdminDto> listUniversities();
    UniversityAdminDto getUniversity(UUID id);
    UniversityAdminDto createUniversity(CatalogCommands.CreateUniversity cmd);
    UniversityAdminDto updateUniversity(UUID id, CatalogCommands.UpdateUniversity cmd);
    void deleteUniversity(UUID id);

    // ─── Departments ──────────────────────────────────────────────────────────
    List<DepartmentAdminDto> listDepartments(UUID universityId);
    DepartmentAdminDto getDepartment(UUID id);
    DepartmentAdminDto createDepartment(CatalogCommands.CreateDepartment cmd);
    DepartmentAdminDto updateDepartment(UUID id, CatalogCommands.UpdateDepartment cmd);
    void deleteDepartment(UUID id);

    // ─── Terms ────────────────────────────────────────────────────────────────
    List<TermAdminDto> listTerms(UUID universityId);
    TermAdminDto getTerm(UUID id);
    TermAdminDto createTerm(CatalogCommands.CreateTerm cmd);
    TermAdminDto updateTerm(UUID id, CatalogCommands.UpdateTerm cmd);
    void deleteTerm(UUID id);

    // ─── Courses ──────────────────────────────────────────────────────────────
    Page<CourseAdminDto> searchCourses(UUID universityId, String q, Pageable pageable);
    CourseAdminDetail getCourseDetail(UUID id);
    CourseAdminDto createCourse(CatalogCommands.CreateCourse cmd);
    CourseAdminDto updateCourse(UUID id, CatalogCommands.UpdateCourse cmd);

    /**
     * Unconditional delete. Cascades through {@code course_departments} and
     * {@code course_offerings}. The caller must check for active groups first
     * via {@code GroupInternalService}.
     */
    void deleteCourse(UUID id);

    // ─── Course ↔ Department links ────────────────────────────────────────────
    List<CourseDepartmentLinkDto> listCourseDepartments(UUID courseId);
    CourseDepartmentLinkDto linkCourseDepartment(UUID courseId, UUID departmentId, boolean primary);
    CourseDepartmentLinkDto setLinkPrimary(UUID courseId, UUID departmentId, boolean primary);
    void unlinkCourseDepartment(UUID courseId, UUID departmentId);

    // ─── Offerings ────────────────────────────────────────────────────────────
    List<OfferingAdminDto> listOfferingsForCourse(UUID courseId);
    OfferingAdminDto getOffering(UUID id);
    OfferingAdminDto createOffering(UUID courseId, UUID termId);

    /** Unconditional delete. Caller checks for active groups first. */
    void deleteOffering(UUID id);

    // ─── Overview helpers ─────────────────────────────────────────────────────
    long countCourses();
    long countCoursesCreatedAfter(Instant since);
    List<CourseAdminDto> findRecentCourses(int limit);
}
