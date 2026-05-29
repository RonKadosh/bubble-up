package com.ronkadosh.bubbleup.chat.api;

import com.ronkadosh.bubbleup.chat.api.dto.*;
import com.ronkadosh.bubbleup.chat.application.ChatCommandService;
import com.ronkadosh.bubbleup.chat.application.ChatQueryService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.CHAT_BASE)
@RequiredArgsConstructor
public class ChatController {

    private final ChatCommandService commands;
    private final ChatQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/rooms")
    public ApiResponse<List<ChatRoomResponse>> getRooms() {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.getRoomsForUser(me));
    }

    @GetMapping("/rooms/{id}")
    public ApiResponse<ChatRoomResponse> getRoom(@PathVariable UUID id) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.getRoom(id, me));
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatRoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.createRoom(request, me));
    }

    /**
     * Cursor pagination: {@code before} is the id of the oldest already-loaded message;
     * server returns N messages older than it, DESC by sentAt. Omit to get the latest N.
     *
     * {@code focus} is an alternative mode used by reply / pin click-to-jump: when set,
     * the server returns a page centered on that message. {@code before} and {@code focus}
     * are mutually exclusive; if both are present, {@code focus} wins.
     */
    @GetMapping("/rooms/{id}/messages")
    public ApiResponse<List<ChatMessageResponse>> getMessages(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID before,
            @RequestParam(required = false) UUID focus,
            @RequestParam(required = false) Integer size) {
        UUID me = currentUserProvider.get().id();
        if (focus != null) {
            return ApiResponse.success(queries.getMessagesAround(id, me, focus, size));
        }
        return ApiResponse.success(queries.getMessages(id, me, before, size));
    }

    @PostMapping("/rooms/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChatMessageResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.sendMessage(id, me, request));
    }

    @PostMapping("/rooms/{id}/read")
    public ApiResponse<Void> markRead(
            @PathVariable UUID id,
            @Valid @RequestBody MarkReadRequest request) {
        UUID me = currentUserProvider.get().id();
        commands.markRead(id, me, request.lastReadMessageId());
        return ApiResponse.ok();
    }

    @PostMapping("/rooms/{id}/messages/{messageId}/pin")
    public ApiResponse<com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent> pinMessage(
            @PathVariable UUID id,
            @PathVariable UUID messageId) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.pinMessage(id, messageId, me));
    }

    @DeleteMapping("/rooms/{id}/messages/{messageId}/pin")
    public ApiResponse<com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent> unpinMessage(
            @PathVariable UUID id,
            @PathVariable UUID messageId) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.unpinMessage(id, messageId, me));
    }

    @GetMapping("/rooms/{id}/pins")
    public ApiResponse<List<ChatMessageResponse>> getPinnedMessages(@PathVariable UUID id) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.getPinnedMessages(id, me));
    }
}
