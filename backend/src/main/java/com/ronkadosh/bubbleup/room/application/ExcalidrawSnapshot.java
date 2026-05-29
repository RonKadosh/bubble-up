package com.ronkadosh.bubbleup.room.application;

import java.util.List;
import java.util.Map;

/**
 * Whiteboard payload that rides our STOMP relay. Opaque to the backend —
 * Excalidraw owns the element schema. We just store and broadcast.
 *
 * <p>{@code elements} is the full ordered element list (Excalidraw's
 * own collab pattern: send the whole array, debounced). {@code appState}
 * carries view state we want to share (selected colors etc.) — we keep
 * the same shape rather than diverging.
 */
public record ExcalidrawSnapshot(
        List<Map<String, Object>> elements,
        Map<String, Object> appState
) {
    public static ExcalidrawSnapshot empty() {
        return new ExcalidrawSnapshot(List.of(), Map.of());
    }
}
