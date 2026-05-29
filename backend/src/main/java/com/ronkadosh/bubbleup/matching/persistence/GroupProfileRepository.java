package com.ronkadosh.bubbleup.matching.persistence;

import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupProfileRepository extends JpaRepository<GroupProfile, UUID> {
    Optional<GroupProfile> findByGroupId(UUID groupId);
    List<GroupProfile> findAllByGroupIdIn(Collection<UUID> groupIds);
}
