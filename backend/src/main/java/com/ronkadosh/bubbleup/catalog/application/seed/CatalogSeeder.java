package com.ronkadosh.bubbleup.catalog.application.seed;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a {@link CatalogSeedData} tree to the database on boot.
 *
 * Idempotent: every node is find-or-create keyed on its natural identifier
 * (university shortCode; (universityId, dept shortCode); (universityId, course code);
 * (universityId, term code); (courseId, departmentId); (courseId, termId)).
 * Re-running the same tree inserts nothing. Extending the tree later (new course,
 * new dept link, new term, new offering) only writes the new rows.
 *
 * Override the seed payload by providing your own {@code @Bean CatalogSeedData} —
 * {@link CatalogSeedConfig} uses {@code @ConditionalOnMissingBean}, so any
 * user-defined bean wins. Disable seeding entirely with
 * {@code app.catalog.seed.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.catalog.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CatalogSeeder implements ApplicationRunner {

    private final CatalogSeedData seedData;
    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CourseDepartmentRepository courseDepartmentRepository;
    private final TermRepository termRepository;
    private final CourseOfferingRepository courseOfferingRepository;

    private int createdUniversities;
    private int createdDepartments;
    private int createdCourses;
    private int createdLinks;
    private int createdTerms;
    private int createdOfferings;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedData.universities().isEmpty()) {
            log.info("CatalogSeeder: empty seed data — skipping");
            return;
        }
        resetCounters();
        for (CatalogSeedData.UniversitySeed uni : seedData.universities()) {
            seedUniversity(uni);
        }
        log.info("CatalogSeeder: created {} universities, {} departments, {} courses, {} course-dept links, {} terms, {} offerings",
                createdUniversities, createdDepartments, createdCourses, createdLinks, createdTerms, createdOfferings);
    }

    private void seedUniversity(CatalogSeedData.UniversitySeed seed) {
        University university = universityRepository.findByShortCode(seed.shortCode())
                .orElseGet(() -> {
                    createdUniversities++;
                    return universityRepository.save(University.builder()
                            .shortCode(seed.shortCode())
                            .name(seed.name())
                            .country(seed.country())
                            .build());
                });

        Map<String, UUID> deptIdsByShortCode = new HashMap<>();
        for (CatalogSeedData.DepartmentSeed dept : seed.departments()) {
            UUID deptId = seedDepartment(university.getId(), dept);
            deptIdsByShortCode.put(dept.shortCode(), deptId);
        }

        Map<String, UUID> courseIdsByCode = new HashMap<>();
        for (CatalogSeedData.CourseSeed course : seed.courses()) {
            UUID courseId = seedCourse(university.getId(), course);
            courseIdsByCode.put(course.code(), courseId);
            for (CatalogSeedData.CourseDepartmentLinkSeed link : course.departments()) {
                UUID deptId = deptIdsByShortCode.get(link.departmentShortCode());
                if (deptId == null) {
                    log.warn("CatalogSeeder: course {} references unknown department '{}' — skipping link",
                            course.code(), link.departmentShortCode());
                    continue;
                }
                seedCourseDepartmentLink(courseId, deptId, link.primary());
            }
        }

        Map<String, UUID> termIdsByCode = new HashMap<>();
        for (CatalogSeedData.TermSeed term : termsOrEmpty(seed)) {
            UUID termId = seedTerm(university.getId(), term);
            termIdsByCode.put(term.code(), termId);
        }

        for (CatalogSeedData.OfferingSeed offering : offeringsOrEmpty(seed)) {
            UUID courseId = courseIdsByCode.get(offering.courseCode());
            UUID termId = termIdsByCode.get(offering.termCode());
            if (courseId == null) {
                log.warn("CatalogSeeder: offering references unknown course '{}' — skipping",
                        offering.courseCode());
                continue;
            }
            if (termId == null) {
                log.warn("CatalogSeeder: offering references unknown term '{}' — skipping",
                        offering.termCode());
                continue;
            }
            seedOffering(courseId, termId);
        }
    }

    private UUID seedDepartment(UUID universityId, CatalogSeedData.DepartmentSeed seed) {
        return departmentRepository.findByUniversityIdAndShortCode(universityId, seed.shortCode())
                .map(Department::getId)
                .orElseGet(() -> {
                    createdDepartments++;
                    Department saved = departmentRepository.save(Department.builder()
                            .universityId(universityId)
                            .shortCode(seed.shortCode())
                            .name(seed.name())
                            .build());
                    return saved.getId();
                });
    }

    private UUID seedCourse(UUID universityId, CatalogSeedData.CourseSeed seed) {
        return courseRepository.findByUniversityIdAndCode(universityId, seed.code())
                .map(Course::getId)
                .orElseGet(() -> {
                    createdCourses++;
                    Course saved = courseRepository.save(Course.builder()
                            .universityId(universityId)
                            .code(seed.code())
                            .name(seed.name())
                            .creditPoints(seed.creditPoints())
                            .description(seed.description())
                            .build());
                    return saved.getId();
                });
    }

    private void seedCourseDepartmentLink(UUID courseId, UUID departmentId, boolean primary) {
        if (courseDepartmentRepository.existsByCourseIdAndDepartmentId(courseId, departmentId)) {
            return;
        }
        courseDepartmentRepository.save(CourseDepartment.builder()
                .courseId(courseId)
                .departmentId(departmentId)
                .primary(primary)
                .build());
        createdLinks++;
    }

    private UUID seedTerm(UUID universityId, CatalogSeedData.TermSeed seed) {
        return termRepository.findByUniversityIdAndCode(universityId, seed.code())
                .map(Term::getId)
                .orElseGet(() -> {
                    createdTerms++;
                    Term saved = termRepository.save(Term.builder()
                            .universityId(universityId)
                            .code(seed.code())
                            .name(seed.name())
                            .kind(seed.kind())
                            .academicYear(seed.academicYear())
                            .startsOn(seed.startsOn())
                            .endsOn(seed.endsOn())
                            .build());
                    return saved.getId();
                });
    }

    private void seedOffering(UUID courseId, UUID termId) {
        if (courseOfferingRepository.existsByCourseIdAndTermId(courseId, termId)) {
            return;
        }
        courseOfferingRepository.save(CourseOffering.builder()
                .courseId(courseId)
                .termId(termId)
                .build());
        createdOfferings++;
    }

    private static java.util.List<CatalogSeedData.TermSeed> termsOrEmpty(CatalogSeedData.UniversitySeed s) {
        return s.terms() == null ? java.util.List.of() : s.terms();
    }

    private static java.util.List<CatalogSeedData.OfferingSeed> offeringsOrEmpty(CatalogSeedData.UniversitySeed s) {
        return s.offerings() == null ? java.util.List.of() : s.offerings();
    }

    private void resetCounters() {
        createdUniversities = 0;
        createdDepartments = 0;
        createdCourses = 0;
        createdLinks = 0;
        createdTerms = 0;
        createdOfferings = 0;
    }
}
