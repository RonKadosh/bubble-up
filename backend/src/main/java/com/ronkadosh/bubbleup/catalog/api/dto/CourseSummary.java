package com.ronkadosh.bubbleup.catalog.api.dto;

import com.ronkadosh.bubbleup.catalog.model.Course;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseSummary(
        UUID id,
        UUID universityId,
        String code,
        String name,
        BigDecimal creditPoints,
        String description,
        List<UUID> departmentIds
) {
    public static CourseSummary from(Course c, List<UUID> departmentIds) {
        return new CourseSummary(
                c.getId(),
                c.getUniversityId(),
                c.getCode(),
                c.getName(),
                c.getCreditPoints(),
                c.getDescription(),
                departmentIds
        );
    }
}
