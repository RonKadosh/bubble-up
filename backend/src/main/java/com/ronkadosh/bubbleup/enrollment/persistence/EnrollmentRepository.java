package com.ronkadosh.bubbleup.enrollment.persistence;

import com.ronkadosh.bubbleup.enrollment.model.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    List<Enrollment> findAllByUserId(UUID userId);

    List<Enrollment> findAllByOfferingId(UUID offeringId);

    List<Enrollment> findAllByUserIdAndOfferingIdIn(UUID userId, Collection<UUID> offeringIds);

    boolean existsByUserIdAndOfferingId(UUID userId, UUID offeringId);

    Optional<Enrollment> findByUserIdAndOfferingId(UUID userId, UUID offeringId);
}
