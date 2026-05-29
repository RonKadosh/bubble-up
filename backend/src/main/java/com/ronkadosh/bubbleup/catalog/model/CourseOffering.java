package com.ronkadosh.bubbleup.catalog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "course_offerings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_offerings_course_term",
                columnNames = {"course_id", "term_id"}
        ),
        indexes = {
                @Index(name = "idx_course_offerings_course", columnList = "course_id"),
                @Index(name = "idx_course_offerings_term", columnList = "term_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "term_id", nullable = false, updatable = false)
    private UUID termId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
