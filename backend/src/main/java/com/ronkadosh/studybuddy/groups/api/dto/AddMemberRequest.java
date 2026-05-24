package com.ronkadosh.studybuddy.groups.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMemberRequest(
        @NotNull UUID userId
) {}
