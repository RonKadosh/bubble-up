package com.ronkadosh.bubbleup.expert.persistence;

import com.ronkadosh.bubbleup.expert.model.ExpertSession;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpertSessionRepository extends JpaRepository<ExpertSession, UUID> {
    List<ExpertSession> findByExpertProfileIdOrderByCreatedAtDesc(UUID expertProfileId);
    List<ExpertSession> findByExpertUserIdOrderByCreatedAtDesc(UUID expertUserId);
    List<ExpertSession> findByStatusOrderByCreatedAtDesc(ExpertSessionStatus status);
}
