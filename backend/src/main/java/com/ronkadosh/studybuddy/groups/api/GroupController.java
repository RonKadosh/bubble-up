package com.ronkadosh.studybuddy.groups.api;

import com.ronkadosh.studybuddy.calendar.internal.CalendarInternalService;
import com.ronkadosh.studybuddy.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;
import com.ronkadosh.studybuddy.common.api.ApiPaths;
import com.ronkadosh.studybuddy.common.api.ApiResponse;
import com.ronkadosh.studybuddy.common.context.CurrentUser;
import com.ronkadosh.studybuddy.common.context.CurrentUserProvider;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.groups.api.dto.AddMemberRequest;
import com.ronkadosh.studybuddy.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.studybuddy.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.studybuddy.groups.api.dto.GroupResponse;
import com.ronkadosh.studybuddy.groups.api.dto.TransferOwnershipRequest;
import com.ronkadosh.studybuddy.groups.api.dto.UpdateGroupRequest;
import com.ronkadosh.studybuddy.groups.application.GroupCommandService;
import com.ronkadosh.studybuddy.groups.application.GroupQueryService;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.GROUPS_BASE)
@RequiredArgsConstructor
public class GroupController {

    private final GroupCommandService commands;
    private final GroupQueryService queries;
    private final CurrentUserProvider currentUserProvider;
    private final GroupInternalService groupInternalService;
    private final CalendarInternalService calendarInternalService;

    @GetMapping
    public ApiResponse<List<GroupResponse>> listAll() {
        return ApiResponse.success(queries.getAllGroups());
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
        if (!groupInternalService.groupExists(id)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isMember(id, me)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        Instant resolvedFrom = from != null ? from : defaultMonthStart();
        Instant resolvedTo = to != null ? to : defaultMonthEnd();
        return ApiResponse.success(
                calendarInternalService.getEventsForOwner(CalendarOwnerType.GROUP, id, resolvedFrom, resolvedTo)
        );
    }

    private static Instant defaultMonthStart() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant defaultMonthEnd() {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
