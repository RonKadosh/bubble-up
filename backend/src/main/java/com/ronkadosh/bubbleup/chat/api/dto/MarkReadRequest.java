package com.ronkadosh.bubbleup.chat.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MarkReadRequest(
        @NotNull UUID lastReadMessageId
) {}
