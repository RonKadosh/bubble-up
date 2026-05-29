package com.ronkadosh.bubbleup.catalog.application;

import com.ronkadosh.bubbleup.catalog.internal.CatalogAdminInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CatalogCommands;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseAdminDetail;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.CourseDepartmentLinkDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.DepartmentAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.OfferingAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.TermAdminDto;
import com.ronkadosh.bubbleup.catalog.internal.dto.admin.UniversityAdminDto;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseDepartment;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Department;
import com.ronkadosh.bubbleup.catalog.model.Term;
import com.ronkadosh.bubbleup.catalog.model.University;
import com.ronkadosh.bubbleup.catalog.persistence.CourseDepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.TermRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogAdminInternalServiceImpl implements CatalogAdminInternalService {

    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final TermRepository termRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseDepartmentRepository courseDepartmentRepository;

    // ─── Universities ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<UniversityAdminDto> listUniversities() {
        return universityRepository.findAll().stream()
                .sorted(Comparator.comparing(University::getName))
                .map(CatalogAdminInternalServiceImpl::toUniversityDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UniversityAdminDto getUniversity(UUID id) {
        return universityRepository.findById(id)
                .map(CatalogAdminInternalServiceImpl::toUniversityDto)
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));
    }

    @Override
    @Transactional
    public UniversityAdminDto createUniversity(CatalogCommands.CreateUniversity cmd) {
        universityRepository.findByShortCode(cmd.shortCode()).ifPresent(u -> {
            throw new AppException(ErrorCode.ADMIN_CATALOG_UNIVERSITY_SHORT_CODE_TAKEN);
        });
        University u = University.builder()
                .name(cmd.name())
                .shortCode(cmd.shortCode())
                .country(cmd.country())
                .build();
        try {
            return toUniversityDto(universityRepository.save(u));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_UNIVERSITY_SHORT_CODE_TAKEN);
        }
    }

    @Override
    @Transactional
    public UniversityAdminDto updateUniversity(UUID id, CatalogCommands.UpdateUniversity cmd) {
        University u = universityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.UNIVERSITY_NOT_FOUND));
        if (cmd.shortCode() != null && !cmd.shortCode().equals(u.getShortCode())) {
            universityRepository.findByShortCode(cmd.shortCode()).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new AppException(ErrorCode.ADMIN_CATALOG_UNIVERSITY_SHORT_CODE_TAKEN);
                }
            });
            u.setShortCode(cmd.shortCode());
        }
        if (cmd.name() != null) u.setName(cmd.name());
        if (cmd.country() != null) u.setCountry(cmd.country());
        return toUniversityDto(universityRepository.save(u));
    }

    @Override
    @Transactional
    public void deleteUniversity(UUID id) {
        if (!universityRepository.existsById(id)) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        long deps = departmentRepository.countByUniversityId(id);
        long terms = termRepository.countByUniversityId(id);
        long courses = courseRepository.countByUniversityId(id);
        if (deps + terms + courses > 0) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_UNIVERSITY_HAS_DEPENDENTS);
        }
        universityRepository.deleteById(id);
    }

    // ─── Departments ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentAdminDto> listDepartments(UUID universityId) {
        return departmentRepository.findAllByUniversityId(universityId).stream()
                .sorted(Comparator.comparing(Department::getName))
                .map(CatalogAdminInternalServiceImpl::toDepartmentDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentAdminDto getDepartment(UUID id) {
        return departmentRepository.findById(id)
                .map(CatalogAdminInternalServiceImpl::toDepartmentDto)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public DepartmentAdminDto createDepartment(CatalogCommands.CreateDepartment cmd) {
        if (!universityRepository.existsById(cmd.universityId())) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        departmentRepository.findByUniversityIdAndShortCode(cmd.universityId(), cmd.shortCode())
                .ifPresent(d -> {
                    throw new AppException(ErrorCode.ADMIN_CATALOG_DEPARTMENT_SHORT_CODE_TAKEN);
                });
        Department d = Department.builder()
                .universityId(cmd.universityId())
                .name(cmd.name())
                .shortCode(cmd.shortCode())
                .build();
        try {
            return toDepartmentDto(departmentRepository.save(d));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_DEPARTMENT_SHORT_CODE_TAKEN);
        }
    }

    @Override
    @Transactional
    public DepartmentAdminDto updateDepartment(UUID id, CatalogCommands.UpdateDepartment cmd) {
        Department d = departmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
        if (cmd.shortCode() != null && !cmd.shortCode().equals(d.getShortCode())) {
            departmentRepository.findByUniversityIdAndShortCode(d.getUniversityId(), cmd.shortCode())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new AppException(ErrorCode.ADMIN_CATALOG_DEPARTMENT_SHORT_CODE_TAKEN);
                        }
                    });
            d.setShortCode(cmd.shortCode());
        }
        if (cmd.name() != null) d.setName(cmd.name());
        return toDepartmentDto(departmentRepository.save(d));
    }

    @Override
    @Transactional
    public void deleteDepartment(UUID id) {
        if (!departmentRepository.existsById(id)) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        if (courseDepartmentRepository.countByDepartmentId(id) > 0) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_DEPARTMENT_HAS_COURSES);
        }
        departmentRepository.deleteById(id);
    }

    // ─── Terms ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TermAdminDto> listTerms(UUID universityId) {
        return termRepository.findAllByUniversityIdOrderByStartsOnAsc(universityId).stream()
                .map(CatalogAdminInternalServiceImpl::toTermDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TermAdminDto getTerm(UUID id) {
        return termRepository.findById(id)
                .map(CatalogAdminInternalServiceImpl::toTermDto)
                .orElseThrow(() -> new AppException(ErrorCode.TERM_NOT_FOUND));
    }

    @Override
    @Transactional
    public TermAdminDto createTerm(CatalogCommands.CreateTerm cmd) {
        if (!universityRepository.existsById(cmd.universityId())) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        if (cmd.startsOn() == null || cmd.endsOn() == null || cmd.startsOn().isAfter(cmd.endsOn())) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_TERM_DATES_INVALID);
        }
        termRepository.findByUniversityIdAndCode(cmd.universityId(), cmd.code()).ifPresent(t -> {
            throw new AppException(ErrorCode.ADMIN_CATALOG_TERM_CODE_TAKEN);
        });
        Term t = Term.builder()
                .universityId(cmd.universityId())
                .code(cmd.code())
                .name(cmd.name())
                .kind(cmd.kind())
                .academicYear(cmd.academicYear())
                .startsOn(cmd.startsOn())
                .endsOn(cmd.endsOn())
                .build();
        try {
            return toTermDto(termRepository.save(t));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_TERM_CODE_TAKEN);
        }
    }

    @Override
    @Transactional
    public TermAdminDto updateTerm(UUID id, CatalogCommands.UpdateTerm cmd) {
        Term t = termRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TERM_NOT_FOUND));
        java.time.LocalDate newStarts = cmd.startsOn() != null ? cmd.startsOn() : t.getStartsOn();
        java.time.LocalDate newEnds = cmd.endsOn() != null ? cmd.endsOn() : t.getEndsOn();
        if (newStarts.isAfter(newEnds)) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_TERM_DATES_INVALID);
        }
        if (cmd.code() != null && !cmd.code().equals(t.getCode())) {
            termRepository.findByUniversityIdAndCode(t.getUniversityId(), cmd.code())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new AppException(ErrorCode.ADMIN_CATALOG_TERM_CODE_TAKEN);
                        }
                    });
            t.setCode(cmd.code());
        }
        if (cmd.name() != null) t.setName(cmd.name());
        if (cmd.kind() != null) t.setKind(cmd.kind());
        if (cmd.academicYear() != null) t.setAcademicYear(cmd.academicYear());
        t.setStartsOn(newStarts);
        t.setEndsOn(newEnds);
        return toTermDto(termRepository.save(t));
    }

    @Override
    @Transactional
    public void deleteTerm(UUID id) {
        if (!termRepository.existsById(id)) {
            throw new AppException(ErrorCode.TERM_NOT_FOUND);
        }
        if (courseOfferingRepository.countByTermId(id) > 0) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_TERM_HAS_OFFERINGS);
        }
        termRepository.deleteById(id);
    }

    // ─── Courses ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<CourseAdminDto> searchCourses(UUID universityId, String q, Pageable pageable) {
        // Empty-string sentinel — see backend/CLAUDE.md re: Postgres bytea binding.
        String norm = q == null || q.isBlank() ? "" : q.trim().toLowerCase(Locale.ROOT);
        return courseRepository.searchForAdmin(universityId, norm, pageable)
                .map(CatalogAdminInternalServiceImpl::toCourseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseAdminDetail getCourseDetail(UUID id) {
        Course c = courseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        List<OfferingAdminDto> offerings = courseOfferingRepository.findAllByCourseId(id).stream()
                .map(CatalogAdminInternalServiceImpl::toOfferingDto)
                .toList();
        List<CourseDepartmentLinkDto> links = courseDepartmentRepository.findAllByCourseId(id).stream()
                .map(CatalogAdminInternalServiceImpl::toLinkDto)
                .toList();
        return new CourseAdminDetail(toCourseDto(c), offerings, links);
    }

    @Override
    @Transactional
    public CourseAdminDto createCourse(CatalogCommands.CreateCourse cmd) {
        if (!universityRepository.existsById(cmd.universityId())) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }
        courseRepository.findByUniversityIdAndCode(cmd.universityId(), cmd.code()).ifPresent(x -> {
            throw new AppException(ErrorCode.ADMIN_CATALOG_COURSE_CODE_TAKEN);
        });
        Course c = Course.builder()
                .universityId(cmd.universityId())
                .code(cmd.code())
                .name(cmd.name())
                .creditPoints(cmd.creditPoints())
                .description(cmd.description())
                .build();
        try {
            return toCourseDto(courseRepository.save(c));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_COURSE_CODE_TAKEN);
        }
    }

    @Override
    @Transactional
    public CourseAdminDto updateCourse(UUID id, CatalogCommands.UpdateCourse cmd) {
        Course c = courseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        if (cmd.code() != null && !cmd.code().equals(c.getCode())) {
            courseRepository.findByUniversityIdAndCode(c.getUniversityId(), cmd.code())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new AppException(ErrorCode.ADMIN_CATALOG_COURSE_CODE_TAKEN);
                        }
                    });
            c.setCode(cmd.code());
        }
        if (cmd.name() != null) c.setName(cmd.name());
        if (cmd.creditPoints() != null) c.setCreditPoints(cmd.creditPoints());
        if (cmd.description() != null) c.setDescription(cmd.description());
        return toCourseDto(courseRepository.save(c));
    }

    @Override
    @Transactional
    public void deleteCourse(UUID id) {
        if (!courseRepository.existsById(id)) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }
        // Cascade through join + offerings rows the catalog owns.
        // The caller already verified no groups reference any offering of this course.
        courseDepartmentRepository.deleteByCourseId(id);
        courseOfferingRepository.findAllByCourseId(id).forEach(o ->
                courseOfferingRepository.deleteById(o.getId()));
        courseRepository.deleteById(id);
    }

    // ─── Course ↔ Department links ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CourseDepartmentLinkDto> listCourseDepartments(UUID courseId) {
        return courseDepartmentRepository.findAllByCourseId(courseId).stream()
                .map(CatalogAdminInternalServiceImpl::toLinkDto)
                .toList();
    }

    @Override
    @Transactional
    public CourseDepartmentLinkDto linkCourseDepartment(UUID courseId, UUID departmentId, boolean primary) {
        Course c = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        Department d = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.DEPARTMENT_NOT_FOUND));
        if (!c.getUniversityId().equals(d.getUniversityId())) {
            throw new AppException(ErrorCode.DEPARTMENT_NOT_IN_UNIVERSITY);
        }
        if (courseDepartmentRepository.existsByCourseIdAndDepartmentId(courseId, departmentId)) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_COURSE_DEPARTMENT_ALREADY_LINKED);
        }
        CourseDepartment link = CourseDepartment.builder()
                .courseId(courseId)
                .departmentId(departmentId)
                .primary(primary)
                .build();
        return toLinkDto(courseDepartmentRepository.save(link));
    }

    @Override
    @Transactional
    public CourseDepartmentLinkDto setLinkPrimary(UUID courseId, UUID departmentId, boolean primary) {
        CourseDepartment link = courseDepartmentRepository.findByCourseIdAndDepartmentId(courseId, departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ADMIN_CATALOG_COURSE_DEPARTMENT_NOT_LINKED));
        link.setPrimary(primary);
        return toLinkDto(courseDepartmentRepository.save(link));
    }

    @Override
    @Transactional
    public void unlinkCourseDepartment(UUID courseId, UUID departmentId) {
        CourseDepartment link = courseDepartmentRepository.findByCourseIdAndDepartmentId(courseId, departmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ADMIN_CATALOG_COURSE_DEPARTMENT_NOT_LINKED));
        courseDepartmentRepository.deleteById(link.getId());
    }

    // ─── Offerings ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<OfferingAdminDto> listOfferingsForCourse(UUID courseId) {
        return courseOfferingRepository.findAllByCourseId(courseId).stream()
                .map(CatalogAdminInternalServiceImpl::toOfferingDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OfferingAdminDto getOffering(UUID id) {
        return courseOfferingRepository.findById(id)
                .map(CatalogAdminInternalServiceImpl::toOfferingDto)
                .orElseThrow(() -> new AppException(ErrorCode.OFFERING_NOT_FOUND));
    }

    @Override
    @Transactional
    public OfferingAdminDto createOffering(UUID courseId, UUID termId) {
        if (!courseRepository.existsById(courseId)) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!termRepository.existsById(termId)) {
            throw new AppException(ErrorCode.TERM_NOT_FOUND);
        }
        if (courseOfferingRepository.existsByCourseIdAndTermId(courseId, termId)) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_OFFERING_ALREADY_EXISTS);
        }
        CourseOffering o = CourseOffering.builder()
                .courseId(courseId)
                .termId(termId)
                .build();
        try {
            return toOfferingDto(courseOfferingRepository.save(o));
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ADMIN_CATALOG_OFFERING_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional
    public void deleteOffering(UUID id) {
        if (!courseOfferingRepository.existsById(id)) {
            throw new AppException(ErrorCode.OFFERING_NOT_FOUND);
        }
        courseOfferingRepository.deleteById(id);
    }

    // ─── Overview helpers ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long countCourses() {
        return courseRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCoursesCreatedAfter(Instant since) {
        return courseRepository.countByCreatedAtAfter(since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseAdminDto> findRecentCourses(int limit) {
        List<Course> recent = courseRepository.findTop50ByOrderByCreatedAtDesc();
        if (limit >= recent.size()) {
            return recent.stream().map(CatalogAdminInternalServiceImpl::toCourseDto).toList();
        }
        return recent.subList(0, limit).stream()
                .map(CatalogAdminInternalServiceImpl::toCourseDto)
                .toList();
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private static UniversityAdminDto toUniversityDto(University u) {
        return new UniversityAdminDto(u.getId(), u.getName(), u.getShortCode(), u.getCountry(), u.getCreatedAt());
    }

    private static DepartmentAdminDto toDepartmentDto(Department d) {
        return new DepartmentAdminDto(d.getId(), d.getUniversityId(), d.getName(), d.getShortCode(), d.getCreatedAt());
    }

    private static TermAdminDto toTermDto(Term t) {
        return new TermAdminDto(
                t.getId(), t.getUniversityId(), t.getCode(), t.getName(),
                t.getKind(), t.getAcademicYear(), t.getStartsOn(), t.getEndsOn(), t.getCreatedAt());
    }

    private static CourseAdminDto toCourseDto(Course c) {
        return new CourseAdminDto(c.getId(), c.getUniversityId(), c.getCode(), c.getName(),
                c.getCreditPoints(), c.getDescription(), c.getCreatedAt());
    }

    private static OfferingAdminDto toOfferingDto(CourseOffering o) {
        return new OfferingAdminDto(o.getId(), o.getCourseId(), o.getTermId(), o.getCreatedAt());
    }

    private static CourseDepartmentLinkDto toLinkDto(CourseDepartment cd) {
        return new CourseDepartmentLinkDto(cd.getCourseId(), cd.getDepartmentId(), cd.isPrimary(), cd.getCreatedAt());
    }
}
