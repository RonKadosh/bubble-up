package com.ronkadosh.bubbleup.expert.api.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Partial update — all fields optional. {@code null} means "leave unchanged".
 * Passing an empty set on {@code expertiseTags} replaces the existing tags
 * with an empty set; pass {@code null} to skip touching tags entirely.
 */
public record UpdateExpertProfileRequest(
        @Size(min = 1, max = 140) String headline,
        @Size(max = 2000) String bio,
        @Size(max = 20) Set<@Size(min = 1, max = 64) String> expertiseTags
) {}
