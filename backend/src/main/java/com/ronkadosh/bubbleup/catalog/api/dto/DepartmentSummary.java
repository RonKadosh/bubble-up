package com.ronkadosh.bubbleup.catalog.api.dto;

import com.ronkadosh.bubbleup.catalog.model.Department;

import java.util.UUID;

public record DepartmentSummary(
        UUID id,
        UUID universityId,
        String name,
        String shortCode
) {
    public static DepartmentSummary from(Department d) {
        return new DepartmentSummary(d.getId(), d.getUniversityId(), d.getName(), d.getShortCode());
    }
}
