package com.ronkadosh.bubbleup.matching.internal.dto.admin;

import java.util.UUID;

public record QuizQuestionAdminDto(
        UUID id,
        String textEn,
        String textHe,
        int orderIndex,
        boolean active
) {}
