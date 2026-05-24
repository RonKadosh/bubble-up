package com.ronkadosh.studybuddy.groups.persistence;

import com.ronkadosh.studybuddy.groups.model.GroupMember;
import com.ronkadosh.studybuddy.groups.model.MembershipRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Query("delete from GroupMember m where m.groupId = :groupId and m.userId = :userId")
    int deleteByGroupIdAndUserId(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from GroupMember m where m.groupId = :groupId")
    int deleteAllByGroupId(@Param("groupId") UUID groupId);
}
