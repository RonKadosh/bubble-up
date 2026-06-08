package com.ronkadosh.bubbleup.matchingfeedback.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One accumulated feedback row per (user, group) — the measurement harness for the
 * matching engine. It folds the whole funnel into a single upserted row:
 * <ul>
 *   <li>impression: the bubble was shown to the user as a recommendation ({@code shownCount},
 *       last mode/percent/confidence) — written from {@code RecommendationsShownEvent};</li>
 *   <li>outcome: the user then joined it ({@code joinedAt}) — from {@code GroupJoinedEvent};</li>
 *   <li>explicit: the user rated its fit from the group view ({@code rating},
 *       {@code ratingMatchPercent} resolved at click time).</li>
 * </ul>
 * Aggregated by the admin reflection endpoint to ask "does MATCHED convert/feel better
 * than TRENDING, and does a higher match % predict joins and good-fit ratings?".
 */
@Entity
@Table(
        name = "match_feedback",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_feedback_user_group",
                columnNames = {"user_id", "group_id"}),
        indexes = {
                @Index(name = "idx_match_feedback_user_group", columnList = "user_id, group_id"),
                @Index(name = "idx_match_feedback_mode", columnList = "last_display_mode")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "course_id")
    private UUID courseId;

    // ── Impression (recommendation shown) ────────────────────────────────────
    @Column(name = "first_shown_at")
    private Instant firstShownAt;

    @Column(name = "last_shown_at")
    private Instant lastShownAt;

    @Builder.Default
    @Column(name = "shown_count", nullable = false)
    private int shownCount = 0;

    /** "MATCHED" or "TRENDING" at the last impression. Null until first shown. */
    @Column(name = "last_display_mode", length = 16)
    private String lastDisplayMode;

    @Column(name = "last_match_percent")
    private Integer lastMatchPercent;

    @Builder.Default
    @Column(name = "last_matching_confidence", nullable = false)
    private double lastMatchingConfidence = 0.0;

    // ── Outcome (joined) ─────────────────────────────────────────────────────
    @Column(name = "joined_at")
    private Instant joinedAt;

    // ── Explicit rating (from the group view) ────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "rating", length = 16)
    private FeedbackRating rating;

    /** The user's match % resolved at the moment they rated (cache or live). */
    @Column(name = "rating_match_percent")
    private Integer ratingMatchPercent;

    @Column(name = "rated_at")
    private Instant ratedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
