package com.ronkadosh.bubbleup.room.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rooms",
        indexes = {
                @Index(name = "idx_rooms_calendar_event", columnList = "calendar_event_id"),
                @Index(name = "idx_rooms_group", columnList = "group_id"),
                @Index(name = "idx_rooms_jitsi_room_name", columnList = "jitsi_room_name", unique = true)
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RoomScope scope;

    @Column(name = "calendar_event_id")
    private UUID calendarEventId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "expert_session_id")
    private UUID expertSessionId;

    @Column(name = "chat_room_id", nullable = false)
    private UUID chatRoomId;

    @Column(name = "jitsi_room_name", nullable = false, unique = true, length = 128)
    private String jitsiRoomName;

    @Column(name = "whiteboard_enabled", nullable = false)
    @Builder.Default
    private boolean whiteboardEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /**
     * Stamped by {@code RoomLifecycleScheduler} when the session is hard-closed.
     * Once set, the time-window gate in {@code RoomQueryService} rejects further
     * joins with {@code ROOM_ENDED}. Null while the room is still live.
     */
    @Column(name = "ended_at")
    @Setter
    private Instant endedAt;

    /**
     * Stamped the first time the lifecycle scheduler posts the "ends in 15 min"
     * system message. Prevents duplicate prompts on subsequent ticks. Reset to
     * null on extend, so a fresh warning fires before the new {@code endsAt}.
     */
    @Column(name = "end_warning_sent_at")
    @Setter
    private Instant endWarningSentAt;

    /**
     * EXPERT_SESSION rooms only. Stamped the first tick after the session's
     * registration window closes ({@code event.startsAt - 5min}) when the
     * lifecycle scheduler posts the {@code SYSTEM_EXPERT_SESSION_OPEN}
     * notification into each enrolled group's chat. Idempotency flag — once
     * non-null, the notification has been sent and won't repeat.
     */
    @Column(name = "registration_closed_notified_at")
    @Setter
    private Instant registrationClosedNotifiedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
