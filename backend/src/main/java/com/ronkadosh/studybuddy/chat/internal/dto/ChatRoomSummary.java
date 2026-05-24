package com.ronkadosh.studybuddy.chat.internal.dto;

import java.util.UUID;

public record ChatRoomSummary(
        UUID id,
        String name
) {}
