package com.ronkadosh.bubbleup.room.application;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory whiteboard snapshot store, keyed by {@code roomId}.
 * V1: ephemeral. Lost on restart. Promoted to JSONB persistence in V2.
 *
 * <p>Used so that a joiner who connects to {@code /topic/rooms/{id}/whiteboard}
 * AFTER drawings have happened can seed their local Excalidraw with the
 * current state via {@code GET /api/rooms/{id}/whiteboard}.
 */
@Component
public class WhiteboardRelay {

    private final Map<UUID, ExcalidrawSnapshot> snapshots = new ConcurrentHashMap<>();

    public ExcalidrawSnapshot get(UUID roomId) {
        return snapshots.getOrDefault(roomId, ExcalidrawSnapshot.empty());
    }

    public void put(UUID roomId, ExcalidrawSnapshot snapshot) {
        if (snapshot == null) return;
        snapshots.put(roomId, snapshot);
    }

    public void clear(UUID roomId) {
        snapshots.remove(roomId);
    }
}
