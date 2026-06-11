package com.ronkadosh.bubbleup.groups.api;

import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import com.ronkadosh.bubbleup.groups.api.dto.AddMemberRequest;
import com.ronkadosh.bubbleup.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.bubbleup.groups.api.dto.GroupCandidateResponse;
import com.ronkadosh.bubbleup.groups.api.dto.GroupMemberResponse;
import com.ronkadosh.bubbleup.groups.api.dto.GroupResponse;
import com.ronkadosh.bubbleup.groups.api.dto.TransferOwnershipRequest;
import com.ronkadosh.bubbleup.groups.api.dto.UpdateGroupRequest;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.groups.application.GroupCommandService;
import com.ronkadosh.bubbleup.groups.application.GroupImageCommandService;
import com.ronkadosh.bubbleup.groups.application.GroupQueryService;
import com.ronkadosh.bubbleup.groups.application.GroupQueryService.ImageStream;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.GROUPS_BASE)
@RequiredArgsConstructor
public class GroupController {

    private final GroupCommandService commands;
    private final GroupImageCommandService imageCommands;
    private final GroupQueryService queries;
    private final CurrentUserProvider currentUserProvider;

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
    @RateLimit(limit = 10, windowSeconds = 60, scope = RateLimitScope.PER_USER)
    public ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.createGroup(request, me.id()));
    }

    @PatchMapping("/{id}")
    @RateLimit(limit = 20, windowSeconds = 60, scope = RateLimitScope.PER_USER_PER_GROUP)
    public ApiResponse<GroupResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGroupRequest request
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.updateGroup(id, request, me.id()));
    }

    /** Upload / replace the Bubble's cover image (owner only). Multipart "file" field. */
    @PostMapping("/{id}/image")
    @RateLimit(limit = 5, windowSeconds = 60, scope = RateLimitScope.PER_USER_PER_GROUP)
    public ApiResponse<GroupResponse> uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser me = currentUserProvider.get();
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.GROUP_IMAGE_TYPE_NOT_ALLOWED);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.GROUP_IMAGE_TYPE_NOT_ALLOWED, "Could not read image upload");
        }
        return ApiResponse.success(
                imageCommands.replaceImage(id, me.id(), bytes, file.getContentType(), file.getOriginalFilename()));
    }

    /** Clear the Bubble's cover image (owner only). Falls back to the generated avatar. */
    @DeleteMapping("/{id}/image")
    public ApiResponse<GroupResponse> deleteImage(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(imageCommands.deleteImage(id, me.id()));
    }

    /**
     * Public cover-image stream. Unauthenticated by design so {@code <img src=...>}
     * works without an Authorization header; the {@code ?v={fileId}} query param is
     * just a cache-buster. Mirrors {@code GET /api/users/{id}/avatar}.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID id) {
        ImageStream stream = queries.getImage(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(stream.contentType()));
        headers.setCacheControl(CacheControl
                .maxAge(Duration.ofDays(365))
                .cachePublic()
                .immutable());
        return new ResponseEntity<>(stream.bytes(), headers, 200);
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

    /**
     * Owner-only: students enrolled in this Bubble's offering who aren't members yet —
     * the addable candidates for the member-search picker. Optional {@code q} filters
     * by display name.
     */
    @GetMapping("/{id}/candidates")
    public ApiResponse<List<GroupCandidateResponse>> listCandidates(
            @PathVariable UUID id,
            @RequestParam(required = false) String q
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getAddableCandidates(id, me.id(), q));
    }

    /**
     * Bubbles the caller can invite {@code userId} into — owned, ACTIVE, not full,
     * sharing the target's enrolled offering, target not already a member. Backs the
     * "Invite to Bubble" action on the user card.
     */
    @GetMapping("/invitable-for/{userId}")
    public ApiResponse<List<GroupResponse>> listInvitableFor(@PathVariable UUID userId) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getInvitableGroupsForUser(me.id(), userId));
    }

    @PostMapping("/{id}/join")
    @RateLimit(limit = 15, windowSeconds = 60, scope = RateLimitScope.PER_USER)
    public ApiResponse<GroupMemberResponse> join(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.joinGroup(id, me.id()));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 20, windowSeconds = 60, scope = RateLimitScope.PER_USER_PER_GROUP)
    public ApiResponse<GroupMemberResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request
    ) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.addMember(id, me.id(), request));
    }

    @DeleteMapping("/{id}/members/me")
    @RateLimit(limit = 15, windowSeconds = 60, scope = RateLimitScope.PER_USER)
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
