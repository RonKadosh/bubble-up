package com.ronkadosh.bubbleup.groups.persistence;

import com.ronkadosh.bubbleup.groups.model.UserPresence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserPresenceRepository extends JpaRepository<UserPresence, UUID> {

    List<UserPresence> findAllByUserIdIn(Collection<UUID> userIds);
}
