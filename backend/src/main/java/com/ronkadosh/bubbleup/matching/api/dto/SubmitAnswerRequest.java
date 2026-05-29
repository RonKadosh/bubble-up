package com.ronkadosh.bubbleup.matching.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitAnswerRequest(
        @NotNull UUID questionId,
        @NotNull UUID answerId
) {}
