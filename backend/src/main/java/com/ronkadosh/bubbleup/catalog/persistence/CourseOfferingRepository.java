package com.ronkadosh.bubbleup.catalog.persistence;

import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, UUID> {

    Optional<CourseOffering> findByCourseIdAndTermId(UUID courseId, UUID termId);

    boolean existsByCourseIdAndTermId(UUID courseId, UUID termId);

    List<CourseOffering> findAllByCourseId(UUID courseId);

    List<CourseOffering> findAllByTermId(UUID termId);

    List<CourseOffering> findAllByCourseIdInAndTermId(Collection<UUID> courseIds, UUID termId);

    List<CourseOffering> findAllByCourseIdIn(Collection<UUID> courseIds);

    long countByCourseId(UUID courseId);
    long countByTermId(UUID termId);
}
