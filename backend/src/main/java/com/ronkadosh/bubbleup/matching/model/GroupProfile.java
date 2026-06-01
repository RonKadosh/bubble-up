package com.ronkadosh.bubbleup.matching.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "group_profiles",
        indexes = @Index(name = "idx_group_profiles_group", columnList = "group_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId;

    @Builder.Default @Column(name = "avg_leader_score", nullable = false)       private double avgLeaderScore = 0.0;
    @Builder.Default @Column(name = "avg_planner_score", nullable = false)      private double avgPlannerScore = 0.0;
    @Builder.Default @Column(name = "avg_expert_score", nullable = false)       private double avgExpertScore = 0.0;
    @Builder.Default @Column(name = "avg_creative_score", nullable = false)     private double avgCreativeScore = 0.0;
    @Builder.Default @Column(name = "avg_communicator_score", nullable = false) private double avgCommunicatorScore = 0.0;
    @Builder.Default @Column(name = "avg_team_player_score", nullable = false)  private double avgTeamPlayerScore = 0.0;
    @Builder.Default @Column(name = "avg_challenger_score", nullable = false)   private double avgChallengerScore = 0.0;

    @Builder.Default
    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;

    /**
     * How much we trust this group's characteristic profile:
     * {@code avg(member_confidences) * min(member_count / cap, 1)}. Drives the
     * {@code matching_confidence} blend — a group of weakly-known members yields
     * mostly-trending recommendations. See {@code MatchingScorer.groupConfidence}.
     */
    @Builder.Default
    @Column(name = "group_profile_confidence", nullable = false)
    private double groupProfileConfidence = 0.0;

    // Raw trending signals (counts within configured windows). Stored un-normalized
    // so the normalization caps stay tunable without a recompute. See
    // MatchingScorer.trendingScore. member_count above doubles as the size signal.
    @Builder.Default @Column(name = "trending_activity_count", nullable = false)    private long trendingActivityCount = 0L;
    @Builder.Default @Column(name = "trending_recent_joins", nullable = false)      private int trendingRecentJoins = 0;
    @Builder.Default @Column(name = "trending_upcoming_sessions", nullable = false) private int trendingUpcomingSessions = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Role index: 0=Leader 1=Planner 2=Expert 3=Creative 4=Communicator 5=TeamPlayer 6=Challenger
    public double getAvgScore(int r) {
        return switch (r) {
            case 0 -> avgLeaderScore;
            case 1 -> avgPlannerScore;
            case 2 -> avgExpertScore;
            case 3 -> avgCreativeScore;
            case 4 -> avgCommunicatorScore;
            case 5 -> avgTeamPlayerScore;
            case 6 -> avgChallengerScore;
            default -> throw new IllegalArgumentException("Invalid role index: " + r);
        };
    }

    public void setAvgScore(int r, double value) {
        switch (r) {
            case 0 -> avgLeaderScore = value;
            case 1 -> avgPlannerScore = value;
            case 2 -> avgExpertScore = value;
            case 3 -> avgCreativeScore = value;
            case 4 -> avgCommunicatorScore = value;
            case 5 -> avgTeamPlayerScore = value;
            case 6 -> avgChallengerScore = value;
            default -> throw new IllegalArgumentException("Invalid role index: " + r);
        }
    }
}
