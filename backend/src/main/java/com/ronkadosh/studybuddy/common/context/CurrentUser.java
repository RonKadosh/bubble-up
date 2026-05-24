package com.ronkadosh.studybuddy.common.context;

import java.util.UUID;

public record CurrentUser(
        UUID id,
        String email,
        UserRole role
) {}
