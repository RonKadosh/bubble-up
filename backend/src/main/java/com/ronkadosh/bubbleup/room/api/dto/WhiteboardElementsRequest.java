package com.ronkadosh.bubbleup.room.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Whiteboard snapshot POSTed by a client when it has edits to relay.
 * Mirrors the {@code ExcalidrawSnapshot} application DTO — kept as a separate
 * request type for clarity at the HTTP boundary.
 */
public record WhiteboardElementsRequest(
        List<Map<String, Object>> elements,
        Map<String, Object> appState
) {}
