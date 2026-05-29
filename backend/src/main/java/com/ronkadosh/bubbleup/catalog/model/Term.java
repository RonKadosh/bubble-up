package com.ronkadosh.bubbleup.catalog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "terms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_terms_uni_code",
                columnNames = {"university_id", "code"}
        ),
        indexes = {
                @Index(name = "idx_terms_university", columnList = "university_id"),
                @Index(name = "idx_terms_uni_starts_on", columnList = "university_id, starts_on")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "university_id", nullable = false, updatable = false)
    private UUID universityId;

    @Column(nullable = false, length = 16)
    @Setter
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    private TermKind kind;

    @Column(name = "academic_year", nullable = false)
    @Setter
    private int academicYear;

    @Column(name = "starts_on", nullable = false)
    @Setter
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    @Setter
    private LocalDate endsOn;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
