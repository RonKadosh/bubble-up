package com.ronkadosh.bubbleup.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cross-field validation (option count bounds, dedupe) lives in {@code PollCommandService.createPoll}.
 */
public record CreatePollRequest(
        @NotBlank @Size(max = 500) String question,
        @NotEmpty @Size(min = 2, max = 10) List<@NotBlank @Size(max = 200) String> options,
        Boolean allowMultiple
) {}
