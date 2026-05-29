package com.ronkadosh.bubbleup.expert.persistence;

import com.ronkadosh.bubbleup.expert.model.ExpertSessionGroupEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpertSessionGroupEnrollmentRepository
        extends JpaRepository<ExpertSessionGroupEnrollment, UUID> {

    List<ExpertSessionGroupEnrollment> findByExpertSessionId(UUID expertSessionId);

    List<ExpertSessionGroupEnrollment> findByGroupId(UUID groupId);

    Optional<ExpertSessionGroupEnrollment> findByExpertSessionIdAndGroupId(UUID sessionId, UUID groupId);

    boolean existsByExpertSessionIdAndGroupId(UUID sessionId, UUID groupId);

    long countByExpertSessionId(UUID sessionId);

    void deleteByExpertSessionIdAndGroupId(UUID sessionId, UUID groupId);

    void deleteByExpertSessionId(UUID sessionId);
}
