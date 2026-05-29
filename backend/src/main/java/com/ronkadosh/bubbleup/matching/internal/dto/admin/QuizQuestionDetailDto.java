package com.ronkadosh.bubbleup.matching.internal.dto.admin;

import java.util.List;

public record QuizQuestionDetailDto(
        QuizQuestionAdminDto question,
        List<QuizOptionAdminDto> options
) {}
