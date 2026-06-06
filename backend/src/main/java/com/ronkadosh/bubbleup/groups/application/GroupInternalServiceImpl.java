package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupFileActivityItem;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupSummary;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupFileRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupInternalServiceImpl implements GroupInternalService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupFileRepository groupFileRepository;
    private final CatalogInternalService catalogInternalService;

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
    public boolean isActive(UUID groupId) {
        return groupRepository.findById(groupId)
                .map(g -> g.getStatus() == GroupStatus.ACTIVE)
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
                .filter(g -> g.getStatus() == GroupStatus.ACTIVE)
                .map(g -> new GroupSummary(
                        g.getId(),
                        g.getName(),
                        g.getOfferingId(),
                        resolveCourseId(g.getOfferingId()),
                        0))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getMemberUserIds(UUID groupId) {
        return memberRepository.findAllByGroupId(groupId).stream()
                .map(GroupMember::getUserId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countFilesForGroupSince(UUID groupId, java.time.Instant since) {
        return groupFileRepository.countByGroupIdAndUploadedAtGreaterThan(groupId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public int countRecentJoinsForGroup(UUID groupId, java.time.Instant since) {
        return (int) memberRepository.countByGroupIdAndJoinedAtGreaterThan(groupId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupFileActivityItem> findRecentFilesForGroups(Set<UUID> groupIds, int limit) {
        if (groupIds == null || groupIds.isEmpty()) return List.of();
        return groupFileRepository
                .findByGroupIdInOrderByUploadedAtDesc(groupIds, PageRequest.of(0, limit)).stream()
                .map(f -> new GroupFileActivityItem(
                        f.getId(),
                        f.getGroupId(),
                        f.getUploaderId(),
                        f.getOriginalName(),
                        f.getContentType(),
                        f.getSizeBytes(),
                        f.getUploadedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getCandidateGroupIds(List<UUID> courseIds, UUID excludeUserId, int limitPerCourse) {
        Set<UUID> userGroupIds = memberRepository.findAllByUserId(excludeUserId).stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toSet());

        var pageable = PageRequest.of(0, limitPerCourse);
        List<UUID> result = new ArrayList<>();
        for (UUID courseId : courseIds) {
            // Each course can have multiple offerings (across terms) — fetch them all.
            List<UUID> offeringIds = catalogInternalService.offeringIdsForCourses(List.of(courseId));
            if (offeringIds.isEmpty()) continue;
            List<StudyGroup> groups = userGroupIds.isEmpty()
                    ? groupRepository.findByOfferingIdInAndVisibilityAndStatus(
                            offeringIds, GroupVisibility.PUBLIC, GroupStatus.ACTIVE, pageable)
                    : groupRepository.findByOfferingIdInAndVisibilityAndStatusAndIdNotIn(
                            offeringIds, GroupVisibility.PUBLIC, GroupStatus.ACTIVE, userGroupIds, pageable);
            groups.stream().map(StudyGroup::getId).forEach(result::add);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> getCourseIdForGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .map(StudyGroup::getOfferingId)
                .map(this::resolveCourseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> getOfferingIdForGroup(UUID groupId) {
        return groupRepository.findById(groupId).map(StudyGroup::getOfferingId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getGroupName(UUID groupId) {
        return groupRepository.findById(groupId).map(StudyGroup::getName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> getTopPublicGroupIds(UUID excludeUserId, int limit) {
        Set<UUID> userGroupIds = memberRepository.findAllByUserId(excludeUserId).stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toSet());

        var pageable = PageRequest.of(0, limit);
        List<StudyGroup> groups = userGroupIds.isEmpty()
                ? groupRepository.findTopPublic(GroupVisibility.PUBLIC, GroupStatus.ACTIVE, pageable)
                : groupRepository.findTopPublicExcluding(GroupVisibility.PUBLIC, GroupStatus.ACTIVE, userGroupIds, pageable);
        return groups.stream().map(StudyGroup::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean usersShareAnyGroup(UUID viewer, UUID target) {
        if (viewer == null || target == null) return false;
        if (viewer.equals(target)) return true;
        return memberRepository.existsSharedGroup(viewer, target);
    }

    private UUID resolveCourseId(UUID offeringId) {
        return catalogInternalService.getOfferingRef(offeringId)
                .map(OfferingRef::courseId)
                .orElse(null);
    }
}
