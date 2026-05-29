package com.ronkadosh.bubbleup.expert.application;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory map of "who is currently authorized to write on the whiteboard"
 * per expert session. The host is implicitly always a writer (see
 * {@code RoomCommandService.publishWhiteboardSnapshot}); this registry tracks
 * the additional non-host writers the host has granted.
 *
 * <p>Lifetime semantics mirror {@code room/application/WhiteboardRelay} — the
 * registry is process-local and reset on app restart. Cleared by
 * {@code RoomLifecycleScheduler} when a session ends (slice-2 wires the clear
 * call from {@code ExpertSessionCommandService.cancelSession}; the scheduler
 * extension lands when the lifecycle pass needs to fire it).
 */
@Component
public class WhiteboardWriterRegistry {

    private final ConcurrentHashMap<UUID, Set<UUID>> writersBySession = new ConcurrentHashMap<>();

    public void grant(UUID sessionId, UUID userId) {
        writersBySession.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(userId);
    }

    public void revoke(UUID sessionId, UUID userId) {
        Set<UUID> writers = writersBySession.get(sessionId);
        if (writers != null) writers.remove(userId);
    }

    public boolean isWriter(UUID sessionId, UUID userId) {
        Set<UUID> writers = writersBySession.get(sessionId);
        return writers != null && writers.contains(userId);
    }

    public Set<UUID> writersFor(UUID sessionId) {
        Set<UUID> writers = writersBySession.get(sessionId);
        return writers == null ? Set.of() : Set.copyOf(writers);
    }

    public void clear(UUID sessionId) {
        writersBySession.remove(sessionId);
    }
}
