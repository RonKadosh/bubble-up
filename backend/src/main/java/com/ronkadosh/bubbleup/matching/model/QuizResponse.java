package com.ronkadosh.bubbleup.matching.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "quiz_responses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quiz_responses_user_question",
                columnNames = {"user_id", "question_id"}
        ),
        indexes = @Index(name = "idx_quiz_responses_user", columnList = "user_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;
}
