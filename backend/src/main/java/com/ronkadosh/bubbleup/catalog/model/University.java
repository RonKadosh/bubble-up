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
        name = "universities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_universities_short_code",
                columnNames = "short_code"
        ),
        indexes = @Index(name = "idx_universities_short_code", columnList = "short_code")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column(name = "short_code", nullable = false, length = 16)
    @Setter
    private String shortCode;

    @Column(nullable = false, length = 2)
    @Setter
    private String country;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
