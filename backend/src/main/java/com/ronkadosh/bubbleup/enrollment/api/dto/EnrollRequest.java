package com.ronkadosh.bubbleup.enrollment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollRequest(@NotNull UUID courseId) {}
