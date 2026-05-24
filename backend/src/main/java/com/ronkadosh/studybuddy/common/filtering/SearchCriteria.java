package com.ronkadosh.studybuddy.common.filtering;

public record SearchCriteria(
        String field,
        FilterOperator operator,
        Object value
) {}
