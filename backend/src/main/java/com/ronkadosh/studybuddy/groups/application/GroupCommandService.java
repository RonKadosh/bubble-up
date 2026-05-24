package com.ronkadosh.studybuddy.groups.application;

import com.ronkadosh.studybuddy.auth.internal.AuthInternalService;
import com.ronkadosh.studybuddy.calendar.internal.CalendarInternalService;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;
import com.ronkadosh.studybuddy.chat.internal.ChatInternalService;
import com.ronkadosh.studybuddy.chat.model.ChatMessageType;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.groups.internal.GroupFileInternalService;
import com.ronkadosh.studybuddy.groups.api.dto.AddMemberRequest;
import com.ronkadosh.studybuddy.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.studybuddy.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.studybuddy.groups.api.dto.GroupResponse;
import com.ronkadosh.studybuddy.groups.api.dto.TransferOwnershipRequest;
import com.ronkadosh.studybuddy.groups.api.dto.UpdateGroupRequest;
import com.ronkadosh.studybuddy.groups.model.GroupMember;
import com.ronkadosh.studybuddy.groups.model.GroupVisibility;
import com.ronkadosh.studybuddy.groups.model.MembershipRole;
import com.ronkadosh.studybuddy.groups.model.StudyGroup;
import com.ronkadosh.studybuddy.groups.persistence.GroupMemberRepository;
import com.ronkadosh.studybuddy.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupCommandService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final AuthInternalService authInternalService;
    private final ChatInternalService chatInternalService;
    private final GroupFileInternalService groupFileInternalService;
    private final CalendarInternalService calendarInternalService;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID requesterId) {
        StudyGroup group = StudyGroup.builder()
                .name(request.name())
                .description(request.description())
                .visibility(request.visibility() != null ? request.visibility() : GroupVisibility.PUBLIC)
                .createdBy(requesterId)
                .build();
        groupRepository.save(group);
        memberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(requesterId)
                .role(MembershipRole.OWNER)
                .build());
        // Hub UX: every group ships with a default room so the Chat tab is never empty.
        chatInternalService.createRoomForGroup(group.getId(), "general");
        return GroupResponse.from(group, requesterId, 1);
    }

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID requesterId) {
        StudyGroup group = findGroup(groupId);
        requireOwner(groupId, requesterId);
        if (request.name() != null) group.setName(request.name());
        if (request.description() != null) group.setDescription(request.description());
        if (request.visibility() != null) group.setVisibility(request.visibility());
        UUID ownerId = findOwnerId(groupId);
        long count = memberRepository.countByGroupId(groupId);
        return GroupResponse.from(group, ownerId, count);
    }

    @Transactional
    public GroupMemberResponse joinGroup(UUID groupId, UUID requesterId) {
        StudyGroup group = findGroup(groupId);
        if (group.getVisibility() != GroupVisibility.PUBLIC) {
            throw new AppException(ErrorCode.GROUP_NOT_PUBLIC);
        }
        if (memberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
            throw new AppException(ErrorCode.ALREADY_GROUP_MEMBER);
        }
        GroupMember member = memberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .userId(requesterId)
                .role(MembershipRole.MEMBER)
                .build());
        chatInternalService.postSystemMessage(
                groupId, ChatMessageType.SYSTEM_JOIN, requesterId, displayFor(requesterId));
        return GroupMemberResponse.from(member);
    }

    @Transactional
    public GroupMemberResponse addMember(UUID groupId, UUID requesterId, AddMemberRequest request) {
        findGroup(groupId);
        requireOwner(groupId, requesterId);
        UUID targetUserId = request.userId();
        if (!authInternalService.userExists(targetUserId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (memberRepository.existsByGroupIdAndUserId(groupId, targetUserId)) {
            throw new AppException(ErrorCode.ALREADY_GROUP_MEMBER);
        }
        GroupMember member = memberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .userId(targetUserId)
                .role(MembershipRole.MEMBER)
                .build());
        return GroupMemberResponse.from(member);
    }

    @Transactional
    public void leaveGroup(UUID groupId, UUID requesterId) {
        findGroup(groupId);
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, requesterId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_GROUP_MEMBER));
        if (member.getRole() == MembershipRole.OWNER) {
            long otherCount = memberRepository.countByGroupId(groupId) - 1;
            if (otherCount > 0) {
                throw new AppException(ErrorCode.OWNER_MUST_TRANSFER_OR_EMPTY);
            }
            cascadeDeleteGroup(groupId);   // room is gone — no system message
            return;
        }
        chatInternalService.postSystemMessage(
                groupId, ChatMessageType.SYSTEM_LEAVE, requesterId, displayFor(requesterId));
        memberRepository.deleteByGroupIdAndUserId(groupId, requesterId);
    }

    private String displayFor(UUID userId) {
        return authInternalService.getEmail(userId)
                .orElseGet(() -> userId.toString().substring(0, 8));
    }

    @Transactional
    public void removeMember(UUID groupId, UUID requesterId, UUID targetUserId) {
        findGroup(groupId);
        requireOwner(groupId, requesterId);
        if (targetUserId.equals(requesterId)) {
            throw new AppException(ErrorCode.CANNOT_REMOVE_SELF_USE_LEAVE);
        }
        int removed = memberRepository.deleteByGroupIdAndUserId(groupId, targetUserId);
        if (removed == 0) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    @Transactional
    public void transferOwnership(UUID groupId, UUID currentOwnerId, TransferOwnershipRequest request) {
        findGroup(groupId);
        requireOwner(groupId, currentOwnerId);
        UUID newOwnerId = request.newOwnerId();
        GroupMember currentOwner = memberRepository.findByGroupIdAndUserId(groupId, currentOwnerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_GROUP_OWNER));
        GroupMember newOwner = memberRepository.findByGroupIdAndUserId(groupId, newOwnerId)
                .orElseThrow(() -> new AppException(ErrorCode.NEW_OWNER_NOT_GROUP_MEMBER));
        if (currentOwnerId.equals(newOwnerId)) return;
        currentOwner.setRole(MembershipRole.MEMBER);
        newOwner.setRole(MembershipRole.OWNER);
        memberRepository.save(currentOwner);
        memberRepository.save(newOwner);
    }

    @Transactional
    public void deleteGroup(UUID groupId, UUID requesterId) {
        findGroup(groupId);
        requireOwner(groupId, requesterId);
        long count = memberRepository.countByGroupId(groupId);
        if (count > 1) {
            throw new AppException(ErrorCode.GROUP_NOT_EMPTY);
        }
        cascadeDeleteGroup(groupId);
    }

    private void cascadeDeleteGroup(UUID groupId) {
        groupFileInternalService.deleteFilesForGroup(groupId);
        calendarInternalService.deleteEventsForOwner(CalendarOwnerType.GROUP, groupId);
        chatInternalService.deleteRoomsForGroup(groupId);
        memberRepository.deleteAllByGroupId(groupId);
        groupRepository.deleteById(groupId);
    }

    private StudyGroup findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
    }

    private void requireOwner(UUID groupId, UUID userId) {
        MembershipRole role = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(GroupMember::getRole)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_GROUP_OWNER));
        if (role != MembershipRole.OWNER) {
            throw new AppException(ErrorCode.NOT_GROUP_OWNER);
        }
    }

    private UUID findOwnerId(UUID groupId) {
        return memberRepository.findAllByGroupIdAndRole(groupId, MembershipRole.OWNER).stream()
                .findFirst()
                .map(GroupMember::getUserId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR));
    }
}
