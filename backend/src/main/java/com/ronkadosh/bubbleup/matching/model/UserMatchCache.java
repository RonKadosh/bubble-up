package com.ronkadosh.bubbleup.matching.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_match_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_match_cache_user_group",
                columnNames = {"user_id", "group_id"}
        ),
        indexes = {
                @Index(name = "idx_user_match_cache_user_score", columnList = "user_id, match_score"),
                @Index(name = "idx_user_match_cache_group", columnList = "group_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMatchCache {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /**
     * The group's offering (course+term). Term-precise key — set so a future
     * term-rollover sweep or LIST partition can target rows by ended term without
     * re-resolving group → offering. Nullable for forward-compat with pre-existing
     * rows; populated on every cache rebuild.
     */
    @Column(name = "offering_id")
    private UUID offeringId;

    /** The blended {@code final_score} — the ordering key (see the user-score index). */
    @Column(name = "match_score", nullable = false)
    private double matchScore;

    /**
     * Per-recommendation display mode. MATCHED when {@code matchingConfidence} cleared
     * the threshold (show {@link #matchPercent}); TRENDING otherwise (show reason labels,
     * no percent). Replaces the old batch-level result type — each row carries its own.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 16)
    private MatchResultType resultType;

    /** user_confidence × group_profile_confidence at cache time — drives {@link #resultType}. */
    @Builder.Default
    @Column(name = "matching_confidence", nullable = false)
    private double matchingConfidence = 0.0;

    /** round(personalized_match × 100); null for TRENDING rows (no trustworthy percent). */
    @Column(name = "match_percent")
    private Integer matchPercent;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;
}
