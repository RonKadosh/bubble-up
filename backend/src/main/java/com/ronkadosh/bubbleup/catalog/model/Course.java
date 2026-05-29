package com.ronkadosh.bubbleup.catalog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "courses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_courses_uni_code",
                columnNames = {"university_id", "code"}
        ),
        indexes = @Index(name = "idx_courses_university", columnList = "university_id")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "university_id", nullable = false, updatable = false)
    private UUID universityId;

    @Column(nullable = false, length = 32)
    @Setter
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column(name = "credit_points", precision = 4, scale = 2)
    @Setter
    private BigDecimal creditPoints;

    @Column(length = 2000)
    @Setter
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
