package com.ronkadosh.bubbleup.matching.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response from {@code GET /api/matching/quiz/next}.
 *
 * <p>{@code nextAvailableAt} lets the client self-schedule its next poll without
 * needing to also fetch the cooldown config. It is:
 * <ul>
 *   <li>set to {@code lastShown + cooldown} when {@code hasQuestion = false} due to cooldown,</li>
 *   <li>set to {@code now + cooldown} when {@code hasQuestion = true} (the moment a fresh
 *       question becomes available <em>after this one is shown</em>),</li>
 *   <li>{@code null} when the user has exhausted the pool — the client should stop polling.</li>
 * </ul>
 */
public record NextQuestionResponse(
        boolean hasQuestion,
        UUID questionId,
        String questionText,
        List<QuizOptionDto> options,
        Instant nextAvailableAt
) {
    public static NextQuestionResponse cooldown(Instant nextAvailableAt) {
        return new NextQuestionResponse(false, null, null, null, nextAvailableAt);
    }

    public static NextQuestionResponse exhausted() {
        return new NextQuestionResponse(false, null, null, null, null);
    }

    public static NextQuestionResponse of(UUID questionId, String questionText,
                                          List<QuizOptionDto> options, Instant nextAvailableAt) {
        return new NextQuestionResponse(true, questionId, questionText, options, nextAvailableAt);
    }
}
