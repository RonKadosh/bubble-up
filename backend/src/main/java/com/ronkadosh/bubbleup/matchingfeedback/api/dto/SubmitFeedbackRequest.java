package com.ronkadosh.bubbleup.matchingfeedback.api.dto;

import com.ronkadosh.bubbleup.matchingfeedback.model.FeedbackRating;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Explicit fit rating from the group view: which bubble, and the verdict. */
public record SubmitFeedbackRequest(
        @NotNull UUID groupId,
        @NotNull FeedbackRating sentiment
) {}
