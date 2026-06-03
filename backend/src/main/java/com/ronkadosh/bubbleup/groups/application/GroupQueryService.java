package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.enrollment.internal.EnrollmentInternalService;
import com.ronkadosh.bubbleup.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.bubbleup.groups.api.dto.GroupResponse;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupQueryService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final CatalogInternalService catalogInternalService;
    private final AuthInternalService authInternalService;
    private final CalendarInternalService calendarInternalService;
    private final EnrollmentInternalService enrollmentInternalService;

    @Transactional(readOnly = true)
    public List<GroupResponse> getAllGroups() {
        return groupRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Filtered list. Precedence (most-specific first):
     * <ol>
     *   <li>{@code offeringId} — exact match.</li>
     *   <li>{@code courseId} + optional {@code termId} — narrows to one or all offerings of the course.</li>
     *   <li>{@code departmentId} + optional {@code termId} — expands via catalog.</li>
     *   <li>{@code universityId} + optional {@code termId} — expands via catalog.</li>
     *   <li>All-null delegates to {@link #getAllGroups()}.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsFiltered(UUID offeringId, UUID courseId, UUID departmentId,
                                                 UUID universityId, UUID termId) {
        if (offeringId != null) {
            return mapToResponses(groupRepository.findAllByOfferingId(offeringId));
        }
        if (courseId != null) {
            List<UUID> offeringIds = resolveOfferingIdsForCourse(courseId, termId);
            return mapToResponses(byOfferingIds(offeringIds));
        }
        if (departmentId != null) {
            if (termId != null) {
                return mapToResponses(byOfferingIds(
                        catalogInternalService.offeringIdsForDepartmentAndTerm(departmentId, termId)));
            }
            return mapToResponses(byOfferingIdsForCourses(
                    catalogInternalService.courseIdsForDepartment(departmentId)));
        }
        if (universityId != null) {
            if (termId != null) {
                return mapToResponses(byOfferingIds(
                        catalogInternalService.offeringIdsForUniversityAndTerm(universityId, termId)));
            }
            return mapToResponses(byOfferingIdsForCourses(
                    catalogInternalService.courseIdsForUniversity(universityId)));
        }
        return getAllGroups();
    }

    /**
     * Home-scope feed: bubbles in the caller's university + department + current term.
     * Throws {@link ErrorCode#USER_AFFILIATION_REQUIRED} when affiliation is missing.
     *
     * <p>Widening flags let the same endpoint serve "show me other departments" /
     * "show me other universities" without a second route.
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getRelevant(UUID userId,
                                           boolean includeOtherDepartments,
                                           boolean includeOtherUniversities,
                                           UUID termIdOverride) {
        UserProfile profile = authInternalService.getProfile(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!profile.hasAffiliation()) {
            throw new AppException(ErrorCode.USER_AFFILIATION_REQUIRED);
        }
        UUID universityId = profile.universityId();
        UUID departmentId = profile.departmentId();
        UUID termId = termIdOverride != null
                ? termIdOverride
                : catalogInternalService.currentTermFor(universityId).map(TermRef::id).orElse(null);

        if (includeOtherUniversities) {
            // Cross-uni view: term-scope only (any uni). When no term resolves we
            // gracefully return every public-ish group.
            if (termId == null) return getAllGroups();
            return mapToResponses(byOfferingIds(allOfferingIdsForTerm(termId)));
        }

        if (includeOtherDepartments) {
            return mapToResponses(filterByUniversityAndTerm(universityId, termId));
        }

        // Default: my-uni + my-dept (+ current term if known).
        if (termId == null) {
            // Graceful degradation: no current term — show all bubbles in my dept across terms.
            return mapToResponses(byOfferingIdsForCourses(
                    catalogInternalService.courseIdsForDepartment(departmentId)));
        }
        return mapToResponses(byOfferingIds(
                catalogInternalService.offeringIdsForDepartmentAndTerm(departmentId, termId)));
    }

    /** Groups the caller is currently a member of. */
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(UUID userId) {
        List<UUID> groupIds = memberRepository.findAllByUserId(userId).stream()
                .map(GroupMember::getGroupId)
                .toList();
        if (groupIds.isEmpty()) return List.of();
        return mapToResponses(groupRepository.findAllById(groupIds));
    }

    /**
     * Course Page groups feed. Gated on the caller being enrolled in the course
     * for the current term (ADMINs bypass). Resolves the current offering and
     * lists its groups, optionally filtered by {@code q} (case-insensitive name
     * substring), {@code visibility}, and {@code joinedOnly} (only the groups
     * the caller is a member of).
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getGroupsByCourse(UUID userId, UserRole userRole,
                                                  UUID courseId,
                                                  String q, GroupVisibility visibility,
                                                  boolean joinedOnly) {
        if (userRole != UserRole.ADMIN
                && !enrollmentInternalService.isEnrolledInCourseCurrentTerm(userId, courseId)) {
            throw new AppException(ErrorCode.NOT_ENROLLED_IN_COURSE);
        }
        UserProfile profile = authInternalService.getProfile(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (profile.universityId() == null) {
            throw new AppException(ErrorCode.USER_AFFILIATION_REQUIRED);
        }
        UUID termId = catalogInternalService.currentTermFor(profile.universityId())
                .map(TermRef::id)
                .orElseThrow(() -> new AppException(ErrorCode.CURRENT_TERM_NOT_FOUND));
        UUID offeringId = catalogInternalService.offeringIdForCourseAndTerm(courseId, termId)
                .orElseThrow(() -> new AppException(ErrorCode.OFFERING_NOT_FOUND));
        List<StudyGroup> groups = groupRepository.findAllByOfferingId(offeringId);
        Set<UUID> joinedGroupIds = joinedOnly
                ? memberRepository.findAllByUserId(userId).stream()
                        .map(GroupMember::getGroupId)
                        .collect(java.util.stream.Collectors.toSet())
                : Set.of();
        String norm = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        List<StudyGroup> filtered = groups.stream()
                .filter(g -> visibility == null || g.getVisibility() == visibility)
                .filter(g -> norm == null || norm.isEmpty()
                        || g.getName().toLowerCase(Locale.ROOT).contains(norm))
                .filter(g -> !joinedOnly || joinedGroupIds.contains(g.getId()))
                .toList();
        return mapToResponses(filtered);
    }

    /**
     * Public, not-yet-joined bubbles across the caller's current-term enrolled
     * courses — powers the onboarding "Find a Bubble" step. Returns empty (never
     * throws) when there's no affiliation / current term / enrolment, so the
     * caller can simply offer "create the first one" instead.
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getDiscoverableForMyCourses(UUID userId) {
        UserProfile profile = authInternalService.getProfile(userId).orElse(null);
        if (profile == null || profile.universityId() == null) return List.of();

        UUID termId = catalogInternalService.currentTermFor(profile.universityId())
                .map(TermRef::id)
                .orElse(null);
        if (termId == null) return List.of();

        List<UUID> courseIds = enrollmentInternalService.enrolledCourseIdsForCurrentTerm(userId);
        if (courseIds.isEmpty()) return List.of();

        Set<UUID> offeringIds = new HashSet<>();
        for (UUID courseId : courseIds) {
            catalogInternalService.offeringIdForCourseAndTerm(courseId, termId).ifPresent(offeringIds::add);
        }
        if (offeringIds.isEmpty()) return List.of();

        Set<UUID> joinedGroupIds = memberRepository.findAllByUserId(userId).stream()
                .map(GroupMember::getGroupId)
                .collect(java.util.stream.Collectors.toSet());

        // Newest bubbles first — fresh ones surface ahead of the 50-item cap.
        Pageable cap = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<StudyGroup> groups = joinedGroupIds.isEmpty()
                ? groupRepository.findByOfferingIdInAndVisibility(offeringIds, GroupVisibility.PUBLIC, cap)
                : groupRepository.findByOfferingIdInAndVisibilityAndIdNotIn(
                        offeringIds, GroupVisibility.PUBLIC, joinedGroupIds, cap);
        return mapToResponses(groups);
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
        List<GroupMember> members = memberRepository.findAllByGroupId(groupId);
        if (members.isEmpty()) return List.of();
        Set<UUID> userIds = new HashSet<>(members.size());
        for (GroupMember m : members) userIds.add(m.getUserId());
        Map<UUID, UserIdentity> identities = authInternalService.getIdentitiesByIds(userIds);
        List<GroupMemberResponse> out = new ArrayList<>(members.size());
        for (GroupMember m : members) {
            out.add(GroupMemberResponse.from(m, identities.get(m.getUserId())));
        }
        return out;
    }

    /**
     * Backs {@code GET /api/groups/{id}/events}. Gates on membership and defaults the
     * window to the current calendar month (UTC) when caller omits {@code from}/{@code to}.
     */
    @Transactional(readOnly = true)
    public List<CalendarEventSummary> listGroupEvents(UUID groupId, Instant from, Instant to, UUID requesterId) {
        if (!groupRepository.existsById(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!memberRepository.existsByGroupIdAndUserId(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        Instant resolvedFrom = from != null ? from : defaultMonthStart();
        Instant resolvedTo = to != null ? to : defaultMonthEnd();
        return calendarInternalService.getEventsForOwner(
                CalendarOwnerType.GROUP, groupId, resolvedFrom, resolvedTo);
    }

    private static Instant defaultMonthStart() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant defaultMonthEnd() {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private List<UUID> resolveOfferingIdsForCourse(UUID courseId, UUID termId) {
        if (termId != null) {
            return catalogInternalService.offeringIdForCourseAndTerm(courseId, termId)
                    .map(List::of)
                    .orElse(List.of());
        }
        // Any offering of this course (across all terms).
        return catalogInternalService.offeringIdsForCourses(List.of(courseId));
    }

    private List<UUID> allOfferingIdsForTerm(UUID termId) {
        // No direct lookup; in practice multi-uni "term-only" is rare. Approximated
        // as offerings whose term matches — derived by scanning offerings.
        return catalogInternalService.offeringIdsForTerm(termId);
    }

    private List<StudyGroup> filterByUniversityAndTerm(UUID universityId, UUID termId) {
        if (termId == null) {
            return byOfferingIdsForCourses(catalogInternalService.courseIdsForUniversity(universityId));
        }
        return byOfferingIds(catalogInternalService.offeringIdsForUniversityAndTerm(universityId, termId));
    }

    private List<StudyGroup> byOfferingIds(Collection<UUID> offeringIds) {
        if (offeringIds.isEmpty()) return List.of();
        return groupRepository.findAllByOfferingIdIn(offeringIds);
    }

    private List<StudyGroup> byOfferingIdsForCourses(List<UUID> courseIds) {
        if (courseIds.isEmpty()) return List.of();
        return byOfferingIds(catalogInternalService.offeringIdsForCourses(courseIds));
    }

    private List<GroupResponse> mapToResponses(List<StudyGroup> groups) {
        List<GroupResponse> out = new ArrayList<>(groups.size());
        for (StudyGroup g : groups) out.add(toResponse(g));
        return out;
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
