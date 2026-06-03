package com.ronkadosh.bubbleup.groups.api;

import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.groups.api.dto.AddMemberRequest;
import com.ronkadosh.bubbleup.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.bubbleup.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.bubbleup.groups.api.dto.GroupResponse;
import com.ronkadosh.bubbleup.groups.api.dto.TransferOwnershipRequest;
import com.ronkadosh.bubbleup.groups.api.dto.UpdateGroupRequest;
import com.ronkadosh.bubbleup.groups.application.GroupCommandService;
import com.ronkadosh.bubbleup.groups.application.GroupQueryService;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.GROUPS_BASE)
@RequiredArgsConstructor
public class GroupController {

    private final GroupCommandService commands;
    private final GroupQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<GroupResponse>> listAll(
            @RequestParam(required = false) UUID offeringId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) UUID termId
    ) {
        if (offeringId == null && courseId == null && departmentId == null
                && universityId == null && termId == null) {
            return ApiResponse.success(queries.getAllGroups());
        }
        return ApiResponse.success(queries.getGroupsFiltered(offeringId, courseId, departmentId, universityId, termId));
    }

    /** Groups the caller is currently a member of. Backs the "My Bubbles" hub sidebar. */
    @GetMapping("/me")
    public ApiResponse<List<GroupResponse>> listMine() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getMyGroups(me.id()));
    }

    /**
     * Groups under {@code courseId}'s current-term offering. Enrollment-gated:
     * non-admin callers must be enrolled in the course for the current term or
     * the endpoint returns {@code NOT_ENROLLED_IN_COURSE}.
     */
    @GetMapping("/by-course/{courseId}")
    public ApiResponse<List<GroupResponse>> listByCourse(
            @PathVariable UUID courseId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) GroupVisibility visibility,
            @RequestParam(name = "joinedOnly", defaultValue = "false") boolean joinedOnly
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getGroupsByCourse(
                me.id(), me.role(), courseId, q, visibility, joinedOnly));
    }

    /**
     * Home-scope feed for the caller. Requires affiliation set on the user profile.
     */
    @GetMapping("/relevant")
    public ApiResponse<List<GroupResponse>> listRelevant(
            @RequestParam(name = "includeOtherDepartments", defaultValue = "false") boolean includeOtherDepartments,
            @RequestParam(name = "includeOtherUniversities", defaultValue = "false") boolean includeOtherUniversities,
            @RequestParam(name = "termId", required = false) UUID termIdOverride
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getRelevant(
                me.id(), includeOtherDepartments, includeOtherUniversities, termIdOverride));
    }

    /**
     * Public, not-yet-joined bubbles across the caller's current-term enrolled
     * courses — the onboarding "Find a Bubble" step. Empty (not an error) when the
     * user has no affiliation / current term / enrolment.
     */
    @GetMapping("/discoverable")
    public ApiResponse<List<GroupResponse>> listDiscoverable() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getDiscoverableForMyCourses(me.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(queries.getGroupById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.createGroup(request, me.id()));
    }

    @PatchMapping("/{id}")
    public ApiResponse<GroupResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroupRequest request
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.updateGroup(id, request, me.id()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        commands.deleteGroup(id, me.id());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<GroupMemberResponse>> listMembers(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getMembers(id, me.id()));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<GroupMemberResponse> join(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.joinGroup(id, me.id()));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupMemberResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.addMember(id, me.id(), request));
    }

    @DeleteMapping("/{id}/members/me")
    public ApiResponse<Void> leave(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        commands.leaveGroup(id, me.id());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId
    ) {
        CurrentUser me = currentUserProvider.get();
        commands.removeMember(id, me.id(), userId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/transfer-ownership")
    public ApiResponse<Void> transferOwnership(
            @PathVariable UUID id,
            @Valid @RequestBody TransferOwnershipRequest request
    ) {
        CurrentUser me = currentUserProvider.get();
        commands.transferOwnership(id, me.id(), request);
        return ApiResponse.ok();
    }

    /**
     * Convenience proxy: events on this group's calendar. Defaults to the current
     * calendar month when from/to are omitted. Member-gated.
     */
    @GetMapping("/{id}/events")
    public ApiResponse<List<CalendarEventSummary>> listEvents(
            @PathVariable UUID id,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.listGroupEvents(id, from, to, me));
    }
}
