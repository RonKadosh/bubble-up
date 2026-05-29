package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.common.context.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(
        @NotNull UserRole newRole,
        @NotBlank String reason
) {}
