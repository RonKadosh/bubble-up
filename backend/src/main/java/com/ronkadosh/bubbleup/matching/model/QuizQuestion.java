package com.ronkadosh.bubbleup.matching.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "quiz_questions",
        indexes = @Index(name = "idx_quiz_questions_order", columnList = "order_index")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "text_en", nullable = false, length = 512)
    private String textEn;

    @Column(name = "text_he", length = 512)
    private String textHe;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Returns the question text for the requested ISO language code, falling
     * back to English when the requested translation is missing or blank.
     * Accepts {@code "he"} / {@code "en"}; everything else gets English.
     */
    public String localizedText(String lang) {
        if ("he".equalsIgnoreCase(lang) && textHe != null && !textHe.isBlank()) {
            return textHe;
        }
        return textEn;
    }
}
