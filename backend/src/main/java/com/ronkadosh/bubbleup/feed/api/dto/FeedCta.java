package com.ronkadosh.bubbleup.feed.api.dto;

/**
 * A call-to-action attached to a feed item. {@code type} is one of
 * {@code JOIN_SESSION | JOIN_ROOM | VIEW_BUBBLE | VIEW_EVENT | JOIN_BUBBLE};
 * the frontend maps it to a route, using {@code targetId} (a session/room/group id).
 */
public record FeedCta(String type, String targetId) {}
