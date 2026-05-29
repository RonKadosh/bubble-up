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

    @Column(name = "match_score", nullable = false)
    private double matchScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 16)
    private MatchResultType resultType;

    @Column(name = "cached_at", nullable = false)
    private Instant cachedAt;
}
