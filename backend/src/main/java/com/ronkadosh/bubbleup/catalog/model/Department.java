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
        name = "departments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_departments_uni_short_code",
                columnNames = {"university_id", "short_code"}
        ),
        indexes = @Index(name = "idx_departments_university", columnList = "university_id")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "university_id", nullable = false, updatable = false)
    private UUID universityId;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column(name = "short_code", nullable = false, length = 16)
    @Setter
    private String shortCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
