package com.ronkadosh.bubbleup.matching.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "quiz_answer_options",
        indexes = @Index(name = "idx_quiz_options_question", columnList = "question_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "text_en", nullable = false, length = 512)
    private String textEn;

    @Column(name = "text_he", length = 512)
    private String textHe;

    // Weights — never serialized to HTTP; stored only for score computation
    @Builder.Default @Column(name = "weight_leader")       private double weightLeader = 0.0;
    @Builder.Default @Column(name = "weight_planner")      private double weightPlanner = 0.0;
    @Builder.Default @Column(name = "weight_expert")       private double weightExpert = 0.0;
    @Builder.Default @Column(name = "weight_creative")     private double weightCreative = 0.0;
    @Builder.Default @Column(name = "weight_communicator") private double weightCommunicator = 0.0;
    @Builder.Default @Column(name = "weight_team_player")  private double weightTeamPlayer = 0.0;
    @Builder.Default @Column(name = "weight_challenger")   private double weightChallenger = 0.0;

    public double[] weightsArray() {
        return new double[]{weightLeader, weightPlanner, weightExpert, weightCreative,
                weightCommunicator, weightTeamPlayer, weightChallenger};
    }

    /** See {@link QuizQuestion#localizedText(String)} — same fallback rules. */
    public String localizedText(String lang) {
        if ("he".equalsIgnoreCase(lang) && textHe != null && !textHe.isBlank()) {
            return textHe;
        }
        return textEn;
    }
}
