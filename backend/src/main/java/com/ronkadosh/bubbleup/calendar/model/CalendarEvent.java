package com.ronkadosh.bubbleup.calendar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "calendar_events",
        indexes = {
                @Index(name = "idx_calendar_events_owner", columnList = "owner_type, owner_id"),
                @Index(name = "idx_calendar_events_starts_at", columnList = "starts_at"),
                @Index(name = "idx_calendar_events_owner_starts", columnList = "owner_type, owner_id, starts_at")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private CalendarOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    @Setter
    private CalendarEventType eventType;

    @Column(length = 2000)
    @Setter
    private String description;

    @Column(name = "starts_at", nullable = false)
    @Setter
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    @Setter
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
