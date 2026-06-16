package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.CourseRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.GroupJoinedEvent;
import com.ronkadosh.bubbleup.common.events.GroupMembershipChangedEvent;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.enrollment.internal.EnrollmentInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupFileInternalService;
import com.ronkadosh.bubbleup.groups.api.dto.AddMemberRequest;
import com.ronkadosh.bubbleup.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.bubbleup.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.bubbleup.groups.api.dto.GroupResponse;
import com.ronkadosh.bubbleup.groups.api.dto.TransferOwnershipRequest;
import com.ronkadosh.bubbleup.groups.api.dto.UpdateGroupRequest;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final CatalogInternalService catalogInternalService;
    private final EnrollmentInternalService enrollmentInternalService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID requesterId) {
        UUID offeringId = request.offeringId() != null
                ? request.offeringId()
                : resolveCurrentOfferingForCourse(request.courseId());
        if (!catalogInternalService.offeringExists(offeringId)) {
            throw new AppException(ErrorCode.OFFERING_NOT_FOUND);
        }
        // The creator becomes OWNER, so they must be enrolled in the offering.
        if (!enrollmentInternalService.isEnrolledInOffering(requesterId, offeringId)) {
            throw new AppException(ErrorCode.NOT_ENROLLED_IN_COURSE);
        }
        StudyGroup group = StudyGroup.builder()
                .name(request.name())
                .description(request.description())
                .visibility(request.visibility() != null ? request.visibility() : GroupVisibility.PUBLIC)
                .offeringId(offeringId)
                .maxMembers(request.maxMembers())
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
        eventPublisher.publishEvent(new UserBehaviorEvent(requesterId, BehaviorEventType.CREATED_GROUP));
        return GroupResponse.from(group, requesterId, 1);
    }

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID requesterId) {
        StudyGroup group = findGroup(groupId);
        requireActive(group);
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
        requireActive(group);
        if (!enrollmentInternalService.isEnrolledInOffering(requesterId, group.getOfferingId())) {
            throw new AppException(ErrorCode.NOT_ENROLLED_IN_COURSE);
        }
        if (memberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
            throw new AppException(ErrorCode.ALREADY_GROUP_MEMBER);
        }
        requireCapacity(group);
        GroupMember member = memberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .userId(requesterId)
                .role(MembershipRole.MEMBER)
                .build());
        chatInternalService.postSystemMessage(
                groupId, ChatMessageType.SYSTEM_JOIN, requesterId, displayFor(requesterId));
        eventPublisher.publishEvent(new UserBehaviorEvent(requesterId, BehaviorEventType.JOINED_GROUP));
        eventPublisher.publishEvent(new GroupJoinedEvent(requesterId, groupId));
        eventPublisher.publishEvent(new GroupMembershipChangedEvent(groupId));
        return GroupMemberResponse.from(member, authInternalService.getIdentity(requesterId).orElse(null));
    }

    @Transactional
    public GroupMemberResponse addMember(UUID groupId, UUID requesterId, AddMemberRequest request) {
        StudyGroup group = findGroup(groupId);
        requireOwner(groupId, requesterId);
        requireActive(group);
        UUID targetUserId = request.userId();
        if (!authInternalService.userExists(targetUserId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (!enrollmentInternalService.isEnrolledInOffering(targetUserId, group.getOfferingId())) {
            throw new AppException(ErrorCode.NOT_ENROLLED_IN_COURSE,
                    "User is not enrolled in this Bubble's course");
        }
        if (memberRepository.existsByGroupIdAndUserId(groupId, targetUserId)) {
            throw new AppException(ErrorCode.ALREADY_GROUP_MEMBER);
        }
        requireCapacity(group);
        GroupMember member = memberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .userId(targetUserId)
                .role(MembershipRole.MEMBER)
                .build());
        // Announce the new member in chat — same SYSTEM_JOIN row a self-join posts,
        // so an owner adding someone isn't a silent change.
        chatInternalService.postSystemMessage(
                groupId, ChatMessageType.SYSTEM_JOIN, targetUserId, displayFor(targetUserId));
        eventPublisher.publishEvent(new UserBehaviorEvent(requesterId, BehaviorEventType.ADDED_MEMBER));
        eventPublisher.publishEvent(new GroupMembershipChangedEvent(groupId));
        return GroupMemberResponse.from(member, authInternalService.getIdentity(targetUserId).orElse(null));
    }

    @Transactional
    public void leaveGroup(UUID groupId, UUID requesterId) {
        StudyGroup group = findGroup(groupId);
        requireActive(group);
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
        eventPublisher.publishEvent(new GroupMembershipChangedEvent(groupId));
    }

    /**
     * Label for a member in a SYSTEM_JOIN / SYSTEM_LEAVE chat notice. Prefers the
     * display name (matching the expert-session "X joined the session" row), falls
     * back to email, then a short id when neither is available.
     */
    private String displayFor(UUID userId) {
        String name = authInternalService.getIdentity(userId)
                .map(UserIdentity::displayName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
        if (name != null) return name;
        return authInternalService.getEmail(userId)
                .orElseGet(() -> userId.toString().substring(0, 8));
    }

    @Transactional
    public void removeMember(UUID groupId, UUID requesterId, UUID targetUserId) {
        StudyGroup group = findGroup(groupId);
        requireActive(group);
        requireOwner(groupId, requesterId);
        if (targetUserId.equals(requesterId)) {
            throw new AppException(ErrorCode.CANNOT_REMOVE_SELF_USE_LEAVE);
        }
        int removed = memberRepository.deleteByGroupIdAndUserId(groupId, targetUserId);
        if (removed == 0) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        eventPublisher.publishEvent(new GroupMembershipChangedEvent(groupId));
    }

    @Transactional
    public void transferOwnership(UUID groupId, UUID currentOwnerId, TransferOwnershipRequest request) {
        StudyGroup group = findGroup(groupId);
        requireActive(group);
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
        // Announce the handover in chat so members see who's now in charge.
        chatInternalService.postSystemMessage(
                groupId,
                ChatMessageType.SYSTEM_OWNERSHIP_TRANSFER,
                newOwnerId,
                displayFor(currentOwnerId) + " transferred ownership to " + displayFor(newOwnerId));
    }

    @Transactional
    public void deleteGroup(UUID groupId, UUID requesterId) {
        StudyGroup group = findGroup(groupId);
        requireActive(group);
        requireOwner(groupId, requesterId);
        long count = memberRepository.countByGroupId(groupId);
        if (count > 1) {
            throw new AppException(ErrorCode.GROUP_NOT_EMPTY);
        }
        cascadeDeleteGroup(groupId);
    }

    /**
     * Admin override: skips the owner + emptiness checks but reuses the same cascade
     * as the member-facing delete. Called by {@code GroupAdminInternalServiceImpl}.
     */
    @Transactional
    public void deleteGroupAsAdmin(UUID groupId) {
        findGroup(groupId);
        cascadeDeleteGroup(groupId);
    }

    private void cascadeDeleteGroup(UUID groupId) {
        groupFileInternalService.deleteFilesForGroup(groupId);
        groupFileInternalService.deleteFoldersForGroup(groupId);
        calendarInternalService.deleteEventsForOwner(CalendarOwnerType.GROUP, groupId);
        chatInternalService.deleteRoomsForGroup(groupId);
        memberRepository.deleteAllByGroupId(groupId);
        groupRepository.deleteById(groupId);
    }

    private UUID resolveCurrentOfferingForCourse(UUID courseId) {
        CourseRef course = catalogInternalService.getCourseRef(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        TermRef term = catalogInternalService.currentTermFor(course.universityId())
                .orElseThrow(() -> new AppException(ErrorCode.CURRENT_TERM_NOT_FOUND));
        return catalogInternalService.offeringIdForCourseAndTerm(courseId, term.id())
                .orElseThrow(() -> new AppException(ErrorCode.OFFERING_NOT_FOUND));
    }

    private StudyGroup findGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
    }

    private void requireCapacity(StudyGroup group) {
        if (memberRepository.countByGroupId(group.getId()) >= group.getMaxMembers()) {
            throw new AppException(ErrorCode.GROUP_IS_FULL,
                    "This Bubble is full (max " + group.getMaxMembers() + ")");
        }
    }

    private void requireActive(StudyGroup group) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new AppException(ErrorCode.GROUP_NOT_ACTIVE);
        }
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
