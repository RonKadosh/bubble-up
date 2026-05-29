package com.ronkadosh.bubbleup.expert.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ApplyAsExpertRequest(
        @NotBlank @Size(max = 140) String headline,
        @Size(max = 2000) String bio,
        @Size(max = 20) Set<@Size(min = 1, max = 64) String> expertiseTags
) {}
