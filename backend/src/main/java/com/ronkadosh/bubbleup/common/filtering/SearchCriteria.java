package com.ronkadosh.bubbleup.common.filtering;

public record SearchCriteria(
        String field,
        FilterOperator operator,
        Object value
) {}
