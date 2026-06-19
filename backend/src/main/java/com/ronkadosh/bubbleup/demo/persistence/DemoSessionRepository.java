package com.ronkadosh.bubbleup.demo.persistence;

import com.ronkadosh.bubbleup.demo.model.DemoSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemoSessionRepository extends JpaRepository<DemoSession, UUID> {
    Optional<DemoSession> findByGuestUserId(UUID guestUserId);
    List<DemoSession> findByLastSeenAtBefore(Instant cutoff);
}
