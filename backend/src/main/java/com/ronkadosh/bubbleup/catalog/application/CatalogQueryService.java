package com.ronkadosh.bubbleup.catalog.application;

import com.ronkadosh.bubbleup.catalog.api.dto.CourseSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.DepartmentSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.OfferingSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.TermSummary;
import com.ronkadosh.bubbleup.catalog.api.dto.UniversitySummary;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseDepartment;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Term;
import com.ronkadosh.bubbleup.catalog.persistence.CourseDepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.TermRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CourseDepartmentRepository courseDepartmentRepository;
    private final TermRepository termRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CatalogInternalService catalogInternalService;

    @Transactional(readOnly = true)
    public List<UniversitySummary> listUniversities() {
        return universityRepository.findAll().stream()
                .map(UniversitySummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentSummary> listDepartments(UUID universityId) {
        if (!universityRepository.existsById(universityId)) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        return departmentRepository.findAllByUniversityId(universityId).stream()
                .map(DepartmentSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseSummary> listCoursesByDepartment(UUID departmentId, UUID termId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        if (termId != null && !termRepository.existsById(termId)) {
            throw new AppException(ErrorCode.TERM_NOT_FOUND);
        }
        List<UUID> courseIds = courseDepartmentRepository.findAllByDepartmentId(departmentId).stream()
                .map(CourseDepartment::getCourseId)
                .toList();
        if (courseIds.isEmpty()) return List.of();
        if (termId != null) {
            Set<UUID> offered = courseOfferingRepository.findAllByCourseIdInAndTermId(courseIds, termId).stream()
                    .map(CourseOffering::getCourseId)
                    .collect(Collectors.toSet());
            courseIds = courseIds.stream().filter(offered::contains).toList();
            if (courseIds.isEmpty()) return List.of();
        }
        List<Course> courses = courseRepository.findAllById(courseIds);
        Map<UUID, List<UUID>> deptsByCourse = deptsByCourseFor(courseIds);
        return courses.stream()
                .map(c -> CourseSummary.from(c, deptsByCourse.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    /**
     * Global course search across a university, scoped to a term: only courses with an offering in that term
     * are returned (a course that exists overall but isn't offered in the term never shows). Capped at 50
     * rows — the term filter is applied in the query, so the cap is taken after gating (no missed matches).
     * A blank query returns empty (no all-courses dump).
     */
    @Transactional(readOnly = true)
    public List<CourseSummary> searchCourses(UUID universityId, UUID termId, String q) {
        if (!universityRepository.existsById(universityId)) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        if (!termRepository.existsById(termId)) {
            throw new AppException(ErrorCode.TERM_NOT_FOUND);
        }
        String norm = q == null || q.isBlank() ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) return List.of();
        List<Course> courses = courseRepository
                .searchOfferedInTerm(universityId, norm, termId, PageRequest.of(0, 50, Sort.by("code")))
                .getContent();
        if (courses.isEmpty()) return List.of();
        List<UUID> courseIds = courses.stream().map(Course::getId).toList();
        Map<UUID, List<UUID>> deptsByCourse = deptsByCourseFor(courseIds);
        return courses.stream()
                .map(c -> CourseSummary.from(c, deptsByCourse.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseSummary getCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        List<UUID> deptIds = courseDepartmentRepository.findAllByCourseId(courseId).stream()
                .map(CourseDepartment::getDepartmentId)
                .toList();
        return CourseSummary.from(course, deptIds);
    }

    @Transactional(readOnly = true)
    public List<TermSummary> listTerms(UUID universityId) {
        if (!universityRepository.existsById(universityId)) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        return termRepository.findAllByUniversityIdOrderByStartsOnAsc(universityId).stream()
                .map(TermSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TermSummary getCurrentTerm(UUID universityId) {
        if (!universityRepository.existsById(universityId)) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        return catalogInternalService.currentTermFor(universityId)
                .map(TermSummary::from)
                .orElseThrow(() -> new AppException(ErrorCode.CURRENT_TERM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<OfferingSummary> listOfferingsForCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        List<CourseOffering> offerings = courseOfferingRepository.findAllByCourseId(courseId);
        if (offerings.isEmpty()) return List.of();
        // One course (the same for every offering) + one batched term lookup, instead of
        // re-fetching the offering, its course, and its term per row via getOfferingRef.
        List<UUID> termIds = offerings.stream().map(CourseOffering::getTermId).distinct().toList();
        Map<UUID, Term> termsById = termRepository.findAllById(termIds).stream()
                .collect(Collectors.toMap(Term::getId, t -> t));
        return offerings.stream()
                .map(o -> {
                    Term term = termsById.get(o.getTermId());
                    return term == null ? null : OfferingSummary.from(o, course, term);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public OfferingSummary getCurrentOfferingForCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        UUID termId = catalogInternalService.currentTermFor(course.getUniversityId())
                .orElseThrow(() -> new AppException(ErrorCode.CURRENT_TERM_NOT_FOUND))
                .id();
        UUID offeringId = catalogInternalService.offeringIdForCourseAndTerm(courseId, termId)
                .orElseThrow(() -> new AppException(ErrorCode.OFFERING_NOT_FOUND));
        return catalogInternalService.getOfferingRef(offeringId)
                .map(OfferingSummary::from)
                .orElseThrow(() -> new AppException(ErrorCode.OFFERING_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public OfferingSummary getOffering(UUID offeringId) {
        return catalogInternalService.getOfferingRef(offeringId)
                .map(OfferingSummary::from)
                .orElseThrow(() -> new AppException(ErrorCode.OFFERING_NOT_FOUND));
    }

    private Map<UUID, List<UUID>> deptsByCourseFor(List<UUID> courseIds) {
        return courseDepartmentRepository.findAllByCourseIdIn(courseIds).stream()
                .collect(Collectors.groupingBy(
                        CourseDepartment::getCourseId,
                        Collectors.mapping(CourseDepartment::getDepartmentId, Collectors.toList())
                ));
    }
}
