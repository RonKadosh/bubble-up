package com.ronkadosh.bubbleup.common.api;

public record FieldErrorResponse(
        String field,
        String message
) {}
