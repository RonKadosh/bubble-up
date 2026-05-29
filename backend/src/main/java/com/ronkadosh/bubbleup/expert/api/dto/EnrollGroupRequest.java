package com.ronkadosh.bubbleup.expert.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollGroupRequest(@NotNull UUID groupId) {}
