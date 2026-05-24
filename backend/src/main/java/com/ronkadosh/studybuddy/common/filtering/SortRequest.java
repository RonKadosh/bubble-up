package com.ronkadosh.studybuddy.common.filtering;

public record SortRequest(
        String field,
        String direction
) {
    public SortRequest {
        if (field == null) field = "id";
        if (direction == null) direction = "asc";
    }
}
