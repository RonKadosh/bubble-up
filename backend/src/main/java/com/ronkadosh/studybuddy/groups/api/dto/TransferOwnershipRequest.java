package com.ronkadosh.studybuddy.groups.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(
        @NotNull UUID newOwnerId
) {}
