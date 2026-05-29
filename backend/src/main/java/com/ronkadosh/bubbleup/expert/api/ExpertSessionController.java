package com.ronkadosh.bubbleup.expert.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.expert.api.dto.CreateExpertSessionRequest;
import com.ronkadosh.bubbleup.expert.api.dto.EnrollGroupRequest;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertSessionParticipantResponse;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertSessionResponse;
import com.ronkadosh.bubbleup.expert.api.dto.GrantWhiteboardWriteRequest;
import com.ronkadosh.bubbleup.expert.application.ExpertSessionCommandService;
import com.ronkadosh.bubbleup.expert.application.ExpertSessionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.EXPERT_SESSIONS_BASE)
@RequiredArgsConstructor
public class ExpertSessionController {

    private final ExpertSessionCommandService commands;
    private final ExpertSessionQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpertSessionResponse> create(@Valid @RequestBody CreateExpertSessionRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.createSession(me.id(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpertSessionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(queries.getSession(id));
    }

    @GetMapping("/mine")
    public ApiResponse<List<ExpertSessionResponse>> listMine() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.listForExpert(me.id()));
    }

    /**
     * All currently-OPEN sessions across every expert, sorted by upcoming
     * start time. Returned to any authenticated user — this is the directory
     * surface group owners use to find sessions to enroll into.
     */
    @GetMapping("/open")
    public ApiResponse<List<ExpertSessionResponse>> listOpen() {
        return ApiResponse.success(queries.listOpen());
    }

    @GetMapping("/enrolled")
    public ApiResponse<List<ExpertSessionResponse>> listEnrolled(@RequestParam UUID groupId) {
        return ApiResponse.success(queries.listEnrolledForGroup(groupId));
    }

    /**
     * Host + every enrolled-group member, with display name / avatar / writer
     * flag. Drives the room's host controls UI (manage-participants list).
     * Returned to any authenticated user — same surface area as
     * {@code GET /expert-sessions/{id}}.
     */
    @GetMapping("/{id}/participants")
    public ApiResponse<List<ExpertSessionParticipantResponse>> listParticipants(@PathVariable UUID id) {
        return ApiResponse.success(queries.listParticipants(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        commands.cancelSession(id, me.id());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> enroll(@PathVariable UUID id, @Valid @RequestBody EnrollGroupRequest request) {
        CurrentUser me = currentUserProvider.get();
        commands.enrollGroup(id, request.groupId(), me.id());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/unenroll")
    public ApiResponse<Void> unenroll(@PathVariable UUID id, @Valid @RequestBody EnrollGroupRequest request) {
        CurrentUser me = currentUserProvider.get();
        commands.unenrollGroup(id, request.groupId(), me.id());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/whiteboard/grant")
    public ApiResponse<Void> grantWhiteboardWrite(
            @PathVariable UUID id,
            @Valid @RequestBody GrantWhiteboardWriteRequest request) {
        CurrentUser me = currentUserProvider.get();
        commands.grantWhiteboardWrite(id, request.userId(), me.id());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/whiteboard/revoke")
    public ApiResponse<Void> revokeWhiteboardWrite(
            @PathVariable UUID id,
            @Valid @RequestBody GrantWhiteboardWriteRequest request) {
        CurrentUser me = currentUserProvider.get();
        commands.revokeWhiteboardWrite(id, request.userId(), me.id());
        return ApiResponse.ok();
    }
}
