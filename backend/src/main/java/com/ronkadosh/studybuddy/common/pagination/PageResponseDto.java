package com.ronkadosh.studybuddy.common.pagination;

import java.util.List;

public record PageResponseDto<T>(
        List<T> content,
        int currentPage,
        int totalPages,
        long totalElements,
        int pageSize,
        boolean first,
        boolean last
) {}
