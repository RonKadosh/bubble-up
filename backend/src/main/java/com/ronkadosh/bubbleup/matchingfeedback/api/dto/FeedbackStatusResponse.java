package com.ronkadosh.bubbleup.matchingfeedback.api.dto;

import com.ronkadosh.bubbleup.matchingfeedback.model.FeedbackRating;

/** Whether the caller has already rated a bubble's fit (so the prompt is shown only once). */
public record FeedbackStatusResponse(boolean rated, FeedbackRating sentiment) {}
