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
