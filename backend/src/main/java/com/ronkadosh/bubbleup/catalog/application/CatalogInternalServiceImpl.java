package com.ronkadosh.bubbleup.catalog.application;

import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.CourseRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.DepartmentRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.UniversityRef;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseDepartment;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Department;
import com.ronkadosh.bubbleup.catalog.model.Term;
import com.ronkadosh.bubbleup.catalog.model.TermKind;
import com.ronkadosh.bubbleup.catalog.persistence.CourseDepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.TermRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogInternalServiceImpl implements CatalogInternalService {

    private final CourseRepository courseRepository;
    private final CourseDepartmentRepository courseDepartmentRepository;
    private final DepartmentRepository departmentRepository;
    private final TermRepository termRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final TimeProvider timeProvider;
    private final UniversityRepository universityRepository;

    @Override
    public boolean universityExists(UUID universityId) {
        return universityRepository.existsById(universityId);
    }

    @Override
    public boolean departmentExists(UUID departmentId) {
        return departmentRepository.existsById(departmentId);
    }

    @Override
    public Optional<UniversityRef> getUniversity(UUID universityId) {
        return universityRepository.findById(universityId)
                .map(u -> new UniversityRef(u.getId(), u.getName(), u.getShortCode()));
    }

    @Override
    public Optional<DepartmentRef> getDepartment(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .map(d -> new DepartmentRef(d.getId(), d.getUniversityId(), d.getName()));
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseRepository.existsById(courseId);
    }

    @Override
    public Optional<CourseRef> getCourseRef(UUID courseId) {
        return courseRepository.findById(courseId).map(this::toCourseRef);
    }

    @Override
    public List<UUID> departmentIdsForCourse(UUID courseId) {
        return courseDepartmentRepository.findAllByCourseId(courseId).stream()
                .map(CourseDepartment::getDepartmentId)
                .toList();
    }

    @Override
    public List<UUID> courseIdsForDepartment(UUID departmentId) {
        return courseDepartmentRepository.findAllByDepartmentId(departmentId).stream()
                .map(CourseDepartment::getCourseId)
                .toList();
    }

    @Override
    public List<UUID> courseIdsForUniversity(UUID universityId) {
        return courseRepository.findAllByUniversityId(universityId).stream()
                .map(Course::getId)
                .toList();
    }

    @Override
    public Optional<TermRef> currentTermFor(UUID universityId) {
        LocalDate today = today();
        List<Term> active = termRepository
                .findAllByUniversityIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqual(
                        universityId, today, today);
        if (!active.isEmpty()) {
            // Prefer non-summer, then earliest start. Overlap windows around summer
            // resolve to the main academic term.
            return active.stream()
                    .min(Comparator
                            .comparing((Term t) -> t.getKind() == TermKind.SUMMER)
                            .thenComparing(Term::getStartsOn))
                    .map(this::toTermRef);
        }
        return termRepository
                .findFirstByUniversityIdAndStartsOnAfterOrderByStartsOnAsc(universityId, today)
                .map(this::toTermRef);
    }

    @Override
    public List<TermRef> termsFor(UUID universityId) {
        return termRepository.findAllByUniversityIdOrderByStartsOnAsc(universityId).stream()
                .map(this::toTermRef)
                .toList();
    }

    @Override
    public boolean termExists(UUID termId) {
        return termRepository.existsById(termId);
    }

    @Override
    public boolean offeringExists(UUID offeringId) {
        return courseOfferingRepository.existsById(offeringId);
    }

    @Override
    public Optional<OfferingRef> getOfferingRef(UUID offeringId) {
        return courseOfferingRepository.findById(offeringId).flatMap(this::toOfferingRef);
    }

    @Override
    public Optional<UUID> offeringIdForCourseAndTerm(UUID courseId, UUID termId) {
        return courseOfferingRepository.findByCourseIdAndTermId(courseId, termId)
                .map(CourseOffering::getId);
    }

    @Override
    public Map<UUID, UUID> getCourseIdsByOfferingIds(Collection<UUID> offeringIds) {
        if (offeringIds == null || offeringIds.isEmpty()) return Map.of();
        return courseOfferingRepository.findAllById(offeringIds).stream()
                .collect(Collectors.toMap(CourseOffering::getId, CourseOffering::getCourseId));
    }

    @Override
    public List<UUID> offeringIdsForCourses(java.util.Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) return List.of();
        return courseOfferingRepository.findAllByCourseIdIn(courseIds).stream()
                .map(CourseOffering::getId)
                .toList();
    }

    @Override
    public List<UUID> offeringIdsForTerm(UUID termId) {
        return courseOfferingRepository.findAllByTermId(termId).stream()
                .map(CourseOffering::getId)
                .toList();
    }

    @Override
    public List<UUID> offeringIdsForDepartmentAndTerm(UUID departmentId, UUID termId) {
        List<UUID> courseIds = courseIdsForDepartment(departmentId);
        if (courseIds.isEmpty()) return List.of();
        return courseOfferingRepository.findAllByCourseIdInAndTermId(courseIds, termId).stream()
                .map(CourseOffering::getId)
                .toList();
    }

    @Override
    public List<UUID> offeringIdsForUniversityAndTerm(UUID universityId, UUID termId) {
        List<UUID> courseIds = courseIdsForUniversity(universityId);
        if (courseIds.isEmpty()) return List.of();
        return courseOfferingRepository.findAllByCourseIdInAndTermId(courseIds, termId).stream()
                .map(CourseOffering::getId)
                .toList();
    }

    @Override
    public boolean departmentBelongsToUniversity(UUID departmentId, UUID universityId) {
        return departmentRepository.findById(departmentId)
                .map(Department::getUniversityId)
                .map(universityId::equals)
                .orElse(false);
    }

    private LocalDate today() {
        return timeProvider.now().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private CourseRef toCourseRef(Course c) {
        return new CourseRef(c.getId(), c.getUniversityId(), c.getCode(), c.getName());
    }

    private TermRef toTermRef(Term t) {
        return new TermRef(
                t.getId(), t.getUniversityId(), t.getCode(), t.getName(),
                t.getKind(), t.getAcademicYear(), t.getStartsOn(), t.getEndsOn());
    }

    private Optional<OfferingRef> toOfferingRef(CourseOffering offering) {
        // We need course (for name/code/uni) and term (for code) — one tiny join
        // per call. Acceptable for cross-module lookups; the controller-facing
        // CatalogQueryService can batch if a list endpoint needs it later.
        Course course = courseRepository.findById(offering.getCourseId()).orElse(null);
        Term term = termRepository.findById(offering.getTermId()).orElse(null);
        if (course == null || term == null) return Optional.empty();
        return Optional.of(new OfferingRef(
                offering.getId(),
                course.getId(),
                term.getId(),
                course.getUniversityId(),
                course.getCode(),
                course.getName(),
                term.getCode()
        ));
    }
}
