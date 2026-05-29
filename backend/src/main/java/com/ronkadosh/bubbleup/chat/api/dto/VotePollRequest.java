package com.ronkadosh.bubbleup.chat.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record VotePollRequest(@NotNull List<UUID> optionIds) {}
