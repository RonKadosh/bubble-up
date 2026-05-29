package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminSummary;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.groups.internal.GroupAdminInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.admin.GroupAdminDetail;
import com.ronkadosh.bubbleup.groups.internal.dto.admin.GroupAdminDto;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupAdminInternalServiceImpl implements GroupAdminInternalService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupCommandService groupCommandService;
    private final AuthInternalService authInternalService;
    private final CatalogInternalService catalogInternalService;

    @Override
    @Transactional(readOnly = true)
    public Page<GroupAdminDto> searchForAdmin(String q, Pageable pageable) {
        // Empty-string sentinel — see backend/CLAUDE.md re: Postgres bytea binding.
        String norm = q == null || q.isBlank() ? "" : q.trim().toLowerCase(Locale.ROOT);
        Page<StudyGroup> page = groupRepository.searchForAdmin(norm, pageable);
        BatchLookups lookups = batchLookupsFor(page.getContent());
        return page.map(g -> mapWithLookups(g, lookups));
    }

    @Override
    @Transactional(readOnly = true)
    public GroupAdminDetail getAdminDetail(UUID groupId) {
        StudyGroup g = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
        List<GroupMember> members = memberRepository.findAllByGroupId(groupId);
        List<UUID> userIds = members.stream().map(GroupMember::getUserId).toList();
        var summaries = userIds.stream()
                .map(authInternalService::getAdminSummary)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(java.util.stream.Collectors.toMap(UserAdminSummary::id, s -> s));
        List<GroupAdminDetail.Member> memberDtos = members.stream()
                .map(m -> {
                    UserAdminSummary s = summaries.get(m.getUserId());
                    return new GroupAdminDetail.Member(
                            m.getUserId(),
                            s != null ? s.email() : null,
                            s != null ? s.displayName() : null,
                            m.getRole(),
                            m.getJoinedAt()
                    );
                })
                .toList();
        return new GroupAdminDetail(toDto(g), memberDtos);
    }

    @Override
    public void deleteGroupAsAdmin(UUID groupId) {
        groupCommandService.deleteGroupAsAdmin(groupId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countGroupsForOffering(UUID offeringId) {
        return groupRepository.countByOfferingId(offeringId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countGroupsForOfferings(Collection<UUID> offeringIds) {
        if (offeringIds == null || offeringIds.isEmpty()) return 0;
        return groupRepository.countByOfferingIdIn(offeringIds);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalGroups() {
        return groupRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countGroupsCreatedAfter(Instant since) {
        return groupRepository.countByCreatedAtAfter(since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupAdminDto> findRecentGroups(int limit) {
        List<StudyGroup> recent = groupRepository.findTop50ByOrderByCreatedAtDesc();
        List<StudyGroup> trimmed = limit >= recent.size() ? recent : recent.subList(0, limit);
        BatchLookups lookups = batchLookupsFor(trimmed);
        return trimmed.stream().map(g -> mapWithLookups(g, lookups)).toList();
    }

    /**
     * Two batched lookups for a list of groups: {@code offeringId → courseId}
     * (single round-trip via {@link CatalogInternalService#getCourseIdsByOfferingIds})
     * and {@code groupId → memberCount} (single GROUP BY via
     * {@link GroupMemberRepository#countByGroupIdIn}). Together they let us
     * build the page's DTOs in 3 queries total instead of 1 + 4×N.
     */
    private record BatchLookups(Map<UUID, UUID> courseByOffering, Map<UUID, Long> memberCountByGroup) {
        static BatchLookups empty() {
            return new BatchLookups(Map.of(), Map.of());
        }
    }

    private BatchLookups batchLookupsFor(List<StudyGroup> groups) {
        if (groups.isEmpty()) return BatchLookups.empty();
        Set<UUID> offeringIds = groups.stream().map(StudyGroup::getOfferingId).collect(Collectors.toSet());
        Set<UUID> groupIds = groups.stream().map(StudyGroup::getId).collect(Collectors.toSet());
        Map<UUID, UUID> courseByOffering = catalogInternalService.getCourseIdsByOfferingIds(offeringIds);
        Map<UUID, Long> memberCountByGroup = memberRepository.countByGroupIdIn(groupIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        return new BatchLookups(courseByOffering, memberCountByGroup);
    }

    private GroupAdminDto mapWithLookups(StudyGroup g, BatchLookups l) {
        return new GroupAdminDto(
                g.getId(),
                g.getName(),
                g.getDescription(),
                g.getVisibility(),
                g.getOfferingId(),
                l.courseByOffering().get(g.getOfferingId()),
                g.getCreatedBy(),
                g.getCreatedAt(),
                l.memberCountByGroup().getOrDefault(g.getId(), 0L).intValue()
        );
    }

    /** Single-group conversion used by {@link #getAdminDetail(UUID)} — one detail call. */
    private GroupAdminDto toDto(StudyGroup g) {
        return mapWithLookups(g, batchLookupsFor(List.of(g)));
    }
}
