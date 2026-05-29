package com.ronkadosh.bubbleup.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull UUID groupId
) {}
