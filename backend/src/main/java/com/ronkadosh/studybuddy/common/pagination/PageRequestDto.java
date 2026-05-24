package com.ronkadosh.studybuddy.common.pagination;

public record PageRequestDto(
        int page,
        int size,
        String sortBy,
        String sortDirection
) {
    public PageRequestDto {
        if (page < 0) page = 0;
        if (size < 1) size = 10;
        if (size > 100) size = 100;
        if (sortBy == null) sortBy = "id";
        if (sortDirection == null) sortDirection = "asc";
    }
}
