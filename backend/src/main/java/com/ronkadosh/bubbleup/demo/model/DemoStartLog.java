package com.ronkadosh.bubbleup.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable row per "Start demo" click — the permanent usage tally.
 *
 * <p>Deliberately separate from {@link DemoSession}: a {@code DemoSession} is the
 * live, purgeable world (deleted by the idle-TTL sweep / eager teardown), so it
 * can't be the cumulative count. {@code DemoStartLog} is never purged, so
 * {@code count()} is the all-time number of demo starts and {@code startedAt}
 * supports time-windowed slices (e.g. last 7 days).
 */
@Entity
@Table(
        name = "demo_start_log",
        indexes = @Index(name = "idx_demo_start_log_started", columnList = "started_at")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoStartLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;
}
