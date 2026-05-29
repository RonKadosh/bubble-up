package com.ronkadosh.bubbleup.catalog.api.dto;

import com.ronkadosh.bubbleup.catalog.model.University;

import java.util.UUID;

public record UniversitySummary(
        UUID id,
        String name,
        String shortCode,
        String country
) {
    public static UniversitySummary from(University u) {
        return new UniversitySummary(u.getId(), u.getName(), u.getShortCode(), u.getCountry());
    }
}
