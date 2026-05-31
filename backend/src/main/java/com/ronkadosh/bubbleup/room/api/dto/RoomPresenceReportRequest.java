package com.ronkadosh.bubbleup.room.api.dto;

/**
 * Inbound STOMP body a client sends to {@code /app/rooms/{id}/presence} when it
 * joins or leaves the room's video call. {@code event} is {@code "JOIN"} or
 * {@code "LEAVE"}.
 */
public record RoomPresenceReportRequest(String event) {}
