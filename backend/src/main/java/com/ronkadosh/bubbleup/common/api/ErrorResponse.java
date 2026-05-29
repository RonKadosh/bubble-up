package com.ronkadosh.bubbleup.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String category,
        List<FieldErrorResponse> fields
) {
    public ErrorResponse(String code, String message, String category) {
        this(code, message, category, null);
    }
}
