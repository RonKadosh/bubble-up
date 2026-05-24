package com.ronkadosh.studybuddy.common.filtering;

import java.util.List;

public record FilterRequest(
        List<SearchCriteria> criteria
) {
    public FilterRequest {
        if (criteria == null) criteria = List.of();
    }
}
