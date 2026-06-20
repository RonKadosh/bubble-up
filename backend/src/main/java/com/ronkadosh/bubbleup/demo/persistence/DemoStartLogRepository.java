package com.ronkadosh.bubbleup.demo.persistence;

import com.ronkadosh.bubbleup.demo.model.DemoStartLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface DemoStartLogRepository extends JpaRepository<DemoStartLog, UUID> {
    long countByStartedAtAfter(Instant cutoff);
}
