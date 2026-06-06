package com.ronkadosh.bubbleup.matching.internal;

import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizCommands;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizOptionAdminDto;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizQuestionAdminDto;
import com.ronkadosh.bubbleup.matching.internal.dto.admin.QuizQuestionDetailDto;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only surface over the personality quiz (questions + options + 7 role weights).
 */
public interface MatchingAdminInternalService {

    List<QuizQuestionDetailDto> listAllWithOptions();

    QuizQuestionAdminDto createQuestion(QuizCommands.CreateQuestion cmd);

    QuizQuestionAdminDto updateQuestion(UUID questionId, QuizCommands.UpdateQuestion cmd);

    QuizQuestionAdminDto setActive(UUID questionId, boolean active);

    /** Cascades to delete all options of the question. */
    void deleteQuestion(UUID questionId);

    QuizOptionAdminDto createOption(UUID questionId, QuizCommands.CreateOption cmd);

    QuizOptionAdminDto updateOption(UUID optionId, QuizCommands.UpdateOption cmd);

    void deleteOption(UUID optionId);

    void purgeGroupRecommendations(UUID groupId);

    void purgeGroupRecommendations(List<UUID> groupIds);
}
