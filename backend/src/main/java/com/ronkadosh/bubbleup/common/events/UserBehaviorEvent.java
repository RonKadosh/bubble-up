package com.ronkadosh.bubbleup.common.events;

import java.util.UUID;

public record UserBehaviorEvent(UUID userId, BehaviorEventType eventType) {}
