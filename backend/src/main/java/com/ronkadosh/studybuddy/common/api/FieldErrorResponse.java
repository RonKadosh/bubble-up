package com.ronkadosh.studybuddy.common.api;

public record FieldErrorResponse(
        String field,
        String message
) {}
