package com.ronkadosh.bubbleup.groups.persistence;

import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findAllByGroupId(UUID groupId);

    List<GroupMember> findAllByUserId(UUID userId);

    List<GroupMember> findAllByGroupIdAndRole(UUID groupId, MembershipRole role);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupId(UUID groupId);

    /**
     * Batched member-count query: returns one row per groupId in {@code groupIds}
     * (groups with zero members are absent from the result). Each row is a
     * {@code (UUID groupId, Long count)} pair. Used by the admin list path to
     * avoid one count query per row.
     */
    @Query("select m.groupId, count(m) from GroupMember m where m.groupId in :groupIds group by m.groupId")
    List<Object[]> countByGroupIdIn(@Param("groupIds") Collection<UUID> groupIds);

    @Modifying
    @Query("delete from GroupMember m where m.groupId = :groupId and m.userId = :userId")
    int deleteByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from GroupMember m where m.groupId = :groupId")
    int deleteAllByGroupId(@Param("groupId") UUID groupId);

    /**
     * True iff the viewer and target both belong to at least one common group.
     * Used by the profile-visibility gate. Single round-trip; rides the existing
     * composite index on {@code (group_id, user_id)}.
     */
    @Query("select case when count(m1) > 0 then true else false end "
         + "from GroupMember m1, GroupMember m2 "
         + "where m1.groupId = m2.groupId "
         + "  and m1.userId = :viewer and m2.userId = :target")
    boolean existsSharedGroup(@Param("viewer") UUID viewer, @Param("target") UUID target);
}
