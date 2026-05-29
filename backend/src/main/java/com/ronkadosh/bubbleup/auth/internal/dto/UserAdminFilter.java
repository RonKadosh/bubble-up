package com.ronkadosh.bubbleup.auth.internal.dto;

import com.ronkadosh.bubbleup.common.context.UserRole;

import java.time.Instant;

public record UserAdminFilter(
        UserRole role,
        String q,
        Instant createdAfter
) {}
