package com.ronkadosh.studybuddy.groups.application;

import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import com.ronkadosh.studybuddy.groups.internal.dto.GroupSummary;
import com.ronkadosh.studybuddy.groups.model.GroupMember;
import com.ronkadosh.studybuddy.groups.model.MembershipRole;
import com.ronkadosh.studybuddy.groups.persistence.GroupMemberRepository;
import com.ronkadosh.studybuddy.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupInternalServiceImpl implements GroupInternalService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean groupExists(UUID groupId) {
        return groupRepository.existsById(groupId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UUID groupId, UUID userId) {
        return memberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwner(UUID groupId, UUID userId) {
        return roleOf(groupId, userId)
                .map(role -> role == MembershipRole.OWNER)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipRole> roleOf(UUID groupId, UUID userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(GroupMember::getRole);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupSummary> getGroupsForUser(UUID userId) {
        List<UUID> groupIds = memberRepository.findAllByUserId(userId).stream()
                .map(GroupMember::getGroupId)
                .toList();
        if (groupIds.isEmpty()) return List.of();
        return groupRepository.findAllById(groupIds).stream()
                .map(g -> new GroupSummary(g.getId(), g.getName()))
                .toList();
    }
}
