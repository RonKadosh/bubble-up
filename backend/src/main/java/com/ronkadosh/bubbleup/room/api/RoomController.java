package com.ronkadosh.bubbleup.room.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.room.api.dto.RoomResponse;
import com.ronkadosh.bubbleup.room.api.dto.WhiteboardElementsRequest;
import com.ronkadosh.bubbleup.room.application.ExcalidrawSnapshot;
import com.ronkadosh.bubbleup.room.application.RoomCommandService;
import com.ronkadosh.bubbleup.room.application.RoomQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ROOMS_BASE)
@RequiredArgsConstructor
public class RoomController {

    private final RoomCommandService commands;
    private final RoomQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Group IDs (among the caller's groups) that currently have a live Bubble Room or
     * a joinable enrolled expert session. Drives the red "live" marker on the My
     * Bubbles sidebar. Polled by the hub; cheap (in-memory window checks).
     */
    @GetMapping("/live-groups")
    public ApiResponse<List<UUID>> liveGroups() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.findLiveGroupIds(me.id()));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(@PathVariable UUID roomId) {
        CurrentUser me = currentUserProvider.get();
        RoomResponse response = queries.getRoom(roomId, me);
        commands.recordExpertSessionJoinIfFirst(response.id(), me.id());
        return ApiResponse.success(response);
    }

    /**
     * Convenience lookup: navigate from a calendar-event card without having to
     * remember the room id. Same membership gate as {@code GET /api/rooms/{id}}.
     */
    @GetMapping("/by-event/{eventId}")
    public ApiResponse<RoomResponse> getRoomForEvent(@PathVariable UUID eventId) {
        CurrentUser me = currentUserProvider.get();
        RoomResponse response = queries.getRoomForCalendarEvent(eventId, me);
        commands.recordExpertSessionJoinIfFirst(response.id(), me.id());
        return ApiResponse.success(response);
    }

    @GetMapping("/{roomId}/whiteboard")
    public ApiResponse<ExcalidrawSnapshot> getWhiteboard(@PathVariable UUID roomId) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getCurrentWhiteboard(roomId, me.id()));
    }

    @PostMapping("/{roomId}/whiteboard/elements")
    public ApiResponse<Void> publishWhiteboard(
            @PathVariable UUID roomId,
            @RequestBody WhiteboardElementsRequest request
    ) {
        CurrentUser me = currentUserProvider.get();
        commands.publishWhiteboardSnapshot(
                roomId,
                new ExcalidrawSnapshot(request.elements(), request.appState()),
                me.id()
        );
        return ApiResponse.ok();
    }

    /**
     * Extends the session's {@code endsAt} by a fixed +15 min. Any member of
     * the bubble can call this. Posts a system message in the bubble chat,
     * broadcasts a lifecycle event, and returns the refreshed room with a new
     * JWT whose {@code exp} reflects the new end time.
     */
    @PostMapping("/{roomId}/extend")
    public ApiResponse<RoomResponse> extend(@PathVariable UUID roomId) {
        CurrentUser me = currentUserProvider.get();
        commands.extend(roomId, me.id());
        return ApiResponse.success(queries.getRoom(roomId, me));
    }
}
