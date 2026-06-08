package com.ronkadosh.bubbleup.matching.model;

import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
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

    /**
     * Raw per-action-type event counts. The behavior role vector is <b>derived</b>
     * from these at read time by {@code MatchingScorer.behaviorVector}: each action's
     * count is run through a saturating curve {@code count/(count+k)} and split across
     * roles per config ({@code app.matching.signals}), then max-normalized to a shape.
     * Storing counts (not pre-baked scores) is what lets heavy usage <i>sharpen</i> the
     * profile instead of squashing every role to 1.0, and lets a frequent action (chat)
     * be capped low via its {@code k} so it can't dominate the shape. These same counts
     * also drive behavior_confidence via {@code MatchingScorer.behaviorEvidence} (saturated
     * per action, so diversity — not volume — raises confidence).
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_profile_behavior_counts",
            joinColumns = @JoinColumn(name = "user_profile_id"),
            indexes = @Index(name = "idx_behavior_counts_profile", columnList = "user_profile_id"))
    @MapKeyColumn(name = "event_type", length = 64)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "count", nullable = false)
    @Builder.Default
    private Map<BehaviorEventType, Integer> behaviorCounts = new EnumMap<>(BehaviorEventType.class);

    @Builder.Default
    @Column(name = "answered_questions", nullable = false)
    private int answeredQuestions = 0;

    /**
     * Raw lifetime count of behavior events the user has produced — telemetry only.
     * It does NOT feed confidence: behavior_confidence is derived from the saturated,
     * diversity-aware {@code MatchingScorer.behaviorEvidence} over {@link #behaviorCounts},
     * specifically so spamming one action can't inflate it. Quiz answers are not counted
     * here; they accrue to {@link #answeredQuestions}.
     */
    @Builder.Default
    @Column(name = "meaningful_behavior_events", nullable = false)
    private int meaningfulBehaviorEvents = 0;

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

    /** Records one occurrence of {@code type}. The role vector is derived later from these counts. */
    public void incrementBehaviorCount(BehaviorEventType type) {
        behaviorCounts.merge(type, 1, Integer::sum);
    }
}
