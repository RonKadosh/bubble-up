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
import com.ronkadosh.bubbleup.catalog.persistence.CourseDepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.TermRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
        if (!courseRepository.existsById(courseId)) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }
        return courseOfferingRepository.findAllByCourseId(courseId).stream()
                .map(CourseOffering::getId)
                .map(catalogInternalService::getOfferingRef)
                .flatMap(java.util.Optional::stream)
                .map(OfferingSummary::from)
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
        return courseIds.stream()
                .flatMap(cid -> courseDepartmentRepository.findAllByCourseId(cid).stream())
                .collect(Collectors.groupingBy(
                        CourseDepartment::getCourseId,
                        Collectors.mapping(CourseDepartment::getDepartmentId, Collectors.toList())
                ));
    }
}
