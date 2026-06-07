package com.ronkadosh.bubbleup.matching.persistence;

import com.ronkadosh.bubbleup.matching.model.UserMatchCache;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMatchCacheRepository extends JpaRepository<UserMatchCache, UUID> {

    List<UserMatchCache> findByUserIdOrderByMatchScoreDesc(UUID userId, Pageable pageable);

    List<UserMatchCache> findByUserIdAndCourseIdOrderByMatchScoreDesc(UUID userId, UUID courseId, Pageable pageable);

    Optional<UserMatchCache> findByUserIdAndGroupId(UUID userId, UUID groupId);

    @Modifying
    @Query("DELETE FROM UserMatchCache c WHERE c.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") UUID groupId);

    @Modifying
    @Query("DELETE FROM UserMatchCache c WHERE c.groupId IN :groupIds")
    void deleteByGroupIdIn(@Param("groupIds") Collection<UUID> groupIds);

    @Modifying
    @Query("DELETE FROM UserMatchCache c WHERE c.userId = :userId AND c.groupId NOT IN :keepGroupIds")
    void deleteStaleByUserId(@Param("userId") UUID userId, @Param("keepGroupIds") Collection<UUID> keepGroupIds);

    @Modifying
    @Query("DELETE FROM UserMatchCache c WHERE c.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
