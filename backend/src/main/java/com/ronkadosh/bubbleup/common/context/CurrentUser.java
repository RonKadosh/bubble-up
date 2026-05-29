package com.ronkadosh.bubbleup.common.context;

import java.util.UUID;

public record CurrentUser(
        UUID id,
        String email,
        UserRole role
) {}
