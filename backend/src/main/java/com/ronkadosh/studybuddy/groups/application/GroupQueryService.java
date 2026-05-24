package com.ronkadosh.studybuddy.groups.application;

import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.studybuddy.groups.api.dto.GroupResponse;
import com.ronkadosh.studybuddy.groups.model.GroupMember;
import com.ronkadosh.studybuddy.groups.model.MembershipRole;
import com.ronkadosh.studybuddy.groups.model.StudyGroup;
import com.ronkadosh.studybuddy.groups.persistence.GroupMemberRepository;
import com.ronkadosh.studybuddy.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupQueryService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<GroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupById(UUID id) {
        StudyGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(UUID groupId, UUID requesterId) {
        if (!groupRepository.existsById(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!memberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        return memberRepository.findAllByGroupId(groupId).stream()
                .map(GroupMemberResponse::from)
                .toList();
    }

    private GroupResponse toResponse(StudyGroup group) {
        UUID ownerId = memberRepository.findAllByGroupIdAndRole(group.getId(), MembershipRole.OWNER).stream()
                .findFirst()
                .map(GroupMember::getUserId)
                .orElse(null);
        long count = memberRepository.countByGroupId(group.getId());
        return GroupResponse.from(group, ownerId, count);
    }
}
