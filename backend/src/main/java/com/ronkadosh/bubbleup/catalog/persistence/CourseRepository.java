package com.ronkadosh.bubbleup.catalog.persistence;

import com.ronkadosh.bubbleup.catalog.model.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findAllByUniversityId(UUID universityId);

    Optional<Course> findByUniversityIdAndCode(UUID universityId, String code);

    long countByUniversityId(UUID universityId);
    long countByCreatedAtAfter(Instant since);
    List<Course> findTop50ByOrderByCreatedAtDesc();

    @Query("""
            select c from Course c
            where (:universityId is null or c.universityId = :universityId)
              and (:q = '' or lower(c.code) like concat('%', :q, '%')
                          or lower(c.name) like concat('%', :q, '%'))
            """)
    Page<Course> searchForAdmin(
            @Param("universityId") UUID universityId,
            @Param("q") String q,
            Pageable pageable
    );
}
