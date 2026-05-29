package com.ronkadosh.bubbleup.expert.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantWhiteboardWriteRequest(@NotNull UUID userId) {}
