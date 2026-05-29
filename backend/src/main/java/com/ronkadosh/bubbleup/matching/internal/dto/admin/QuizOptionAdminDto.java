package com.ronkadosh.bubbleup.matching.internal.dto.admin;

import java.util.UUID;

public record QuizOptionAdminDto(
        UUID id,
        UUID questionId,
        String textEn,
        String textHe,
        double weightLeader,
        double weightPlanner,
        double weightExpert,
        double weightCreative,
        double weightCommunicator,
        double weightTeamPlayer,
        double weightChallenger
) {}
