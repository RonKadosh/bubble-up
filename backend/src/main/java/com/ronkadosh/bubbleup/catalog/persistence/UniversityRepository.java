package com.ronkadosh.bubbleup.catalog.persistence;

import com.ronkadosh.bubbleup.catalog.model.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UniversityRepository extends JpaRepository<University, UUID> {
    Optional<University> findByShortCode(String shortCode);
}
