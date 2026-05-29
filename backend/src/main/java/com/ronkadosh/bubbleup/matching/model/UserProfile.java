package com.ronkadosh.bubbleup.matching.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_profiles",
        indexes = @Index(name = "idx_user_profiles_user", columnList = "user_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Builder.Default @Column(name = "quiz_leader_score", nullable = false)       private double quizLeaderScore = 0.0;
    @Builder.Default @Column(name = "quiz_planner_score", nullable = false)      private double quizPlannerScore = 0.0;
    @Builder.Default @Column(name = "quiz_expert_score", nullable = false)       private double quizExpertScore = 0.0;
    @Builder.Default @Column(name = "quiz_creative_score", nullable = false)     private double quizCreativeScore = 0.0;
    @Builder.Default @Column(name = "quiz_communicator_score", nullable = false) private double quizCommunicatorScore = 0.0;
    @Builder.Default @Column(name = "quiz_team_player_score", nullable = false)  private double quizTeamPlayerScore = 0.0;
    @Builder.Default @Column(name = "quiz_challenger_score", nullable = false)   private double quizChallengerScore = 0.0;

    @Builder.Default @Column(name = "behavior_leader_score", nullable = false)       private double behaviorLeaderScore = 0.0;
    @Builder.Default @Column(name = "behavior_planner_score", nullable = false)      private double behaviorPlannerScore = 0.0;
    @Builder.Default @Column(name = "behavior_expert_score", nullable = false)       private double behaviorExpertScore = 0.0;
    @Builder.Default @Column(name = "behavior_creative_score", nullable = false)     private double behaviorCreativeScore = 0.0;
    @Builder.Default @Column(name = "behavior_communicator_score", nullable = false) private double behaviorCommunicatorScore = 0.0;
    @Builder.Default @Column(name = "behavior_team_player_score", nullable = false)  private double behaviorTeamPlayerScore = 0.0;
    @Builder.Default @Column(name = "behavior_challenger_score", nullable = false)   private double behaviorChallengerScore = 0.0;

    @Builder.Default
    @Column(name = "answered_questions", nullable = false)
    private int answeredQuestions = 0;

    @Column(name = "last_question_shown_at")
    private Instant lastQuestionShownAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Role index: 0=Leader 1=Planner 2=Expert 3=Creative 4=Communicator 5=TeamPlayer 6=Challenger
    public double getQuizScore(int r) {
        return switch (r) {
            case 0 -> quizLeaderScore;
            case 1 -> quizPlannerScore;
            case 2 -> quizExpertScore;
            case 3 -> quizCreativeScore;
            case 4 -> quizCommunicatorScore;
            case 5 -> quizTeamPlayerScore;
            case 6 -> quizChallengerScore;
            default -> throw new IllegalArgumentException("Invalid role index: " + r);
        };
    }

    public void setQuizScore(int r, double value) {
        switch (r) {
            case 0 -> quizLeaderScore = value;
            case 1 -> quizPlannerScore = value;
            case 2 -> quizExpertScore = value;
            case 3 -> quizCreativeScore = value;
            case 4 -> quizCommunicatorScore = value;
            case 5 -> quizTeamPlayerScore = value;
            case 6 -> quizChallengerScore = value;
            default -> throw new IllegalArgumentException("Invalid role index: " + r);
        }
    }

    public double getBehaviorScore(int r) {
        return switch (r) {
            case 0 -> behaviorLeaderScore;
            case 1 -> behaviorPlannerScore;
            case 2 -> behaviorExpertScore;
            case 3 -> behaviorCreativeScore;
            case 4 -> behaviorCommunicatorScore;
            case 5 -> behaviorTeamPlayerScore;
            case 6 -> behaviorChallengerScore;
            default -> throw new IllegalArgumentException("Invalid role index: " + r);
        };
    }

    public void addBehaviorDelta(int r, double delta) {
        double current = getBehaviorScore(r);
        double updated = Math.min(1.0, Math.max(0.0, current + delta));
        switch (r) {
            case 0 -> behaviorLeaderScore = updated;
            case 1 -> behaviorPlannerScore = updated;
            case 2 -> behaviorExpertScore = updated;
            case 3 -> behaviorCreativeScore = updated;
            case 4 -> behaviorCommunicatorScore = updated;
            case 5 -> behaviorTeamPlayerScore = updated;
            case 6 -> behaviorChallengerScore = updated;
            default -> throw new IllegalArgumentException("Invalid role index: " + r);
        }
    }
}
