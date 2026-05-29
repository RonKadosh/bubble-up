package com.ronkadosh.bubbleup.catalog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "course_departments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_dept",
                columnNames = {"course_id", "department_id"}
        ),
        indexes = {
                @Index(name = "idx_course_dept_course", columnList = "course_id"),
                @Index(name = "idx_course_dept_department", columnList = "department_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "is_primary", nullable = false)
    @Setter
    private boolean primary;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
