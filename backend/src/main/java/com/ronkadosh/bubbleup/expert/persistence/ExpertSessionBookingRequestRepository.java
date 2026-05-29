package com.ronkadosh.bubbleup.expert.persistence;

import com.ronkadosh.bubbleup.expert.model.ExpertSessionBookingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpertSessionBookingRequestRepository
        extends JpaRepository<ExpertSessionBookingRequest, UUID> {

    List<ExpertSessionBookingRequest> findByExpertUserIdOrderByCreatedAtDesc(UUID expertUserId);

    List<ExpertSessionBookingRequest> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy);
}
