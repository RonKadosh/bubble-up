package com.ronkadosh.bubbleup.expert.persistence;

import com.ronkadosh.bubbleup.expert.model.ExpertProfile;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, UUID> {
    Optional<ExpertProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    List<ExpertProfile> findByVerificationStatus(VerificationStatus status);
    long countByVerificationStatus(VerificationStatus status);
}
