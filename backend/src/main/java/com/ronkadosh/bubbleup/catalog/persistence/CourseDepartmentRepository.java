package com.ronkadosh.bubbleup.catalog.persistence;

import com.ronkadosh.bubbleup.catalog.model.CourseDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseDepartmentRepository extends JpaRepository<CourseDepartment, UUID> {
    List<CourseDepartment> findAllByCourseId(UUID courseId);

    List<CourseDepartment> findAllByDepartmentId(UUID departmentId);

    Optional<CourseDepartment> findByCourseIdAndDepartmentId(UUID courseId, UUID departmentId);

    boolean existsByCourseIdAndDepartmentId(UUID courseId, UUID departmentId);

    long countByDepartmentId(UUID departmentId);
    long countByCourseId(UUID courseId);
    void deleteByCourseId(UUID courseId);
}
