package com.ronkadosh.bubbleup.catalog.persistence;

import com.ronkadosh.bubbleup.catalog.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findAllByUniversityId(UUID universityId);

    Optional<Department> findByUniversityIdAndShortCode(UUID universityId, String shortCode);

    long countByUniversityId(UUID universityId);
}
