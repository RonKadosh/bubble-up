package com.ronkadosh.bubbleup.matching.internal.dto;

/**
 * The matching score for one (user, group) pair at a point in time — read from the
 * user's match cache if present, else scored live (members aren't in their own
 * candidate cache, so the live path is the fallback). {@code matchPercent} is null
 * when the pair scores as TRENDING (no trustworthy percent).
 */
public record MatchSnapshot(Integer matchPercent, double matchingConfidence, String mode) {}
