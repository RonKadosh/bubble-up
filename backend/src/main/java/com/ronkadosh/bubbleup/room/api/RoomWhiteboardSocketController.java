package com.ronkadosh.bubbleup.room.api;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.StompPrincipal;
import com.ronkadosh.bubbleup.room.api.dto.WhiteboardElementsRequest;
import com.ronkadosh.bubbleup.room.application.ExcalidrawSnapshot;
import com.ronkadosh.bubbleup.room.application.RoomCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Inbound STOMP write path for the whiteboard. Clients SEND drawing snapshots to
 * {@code /app/rooms/{roomId}/whiteboard} over the WebSocket they already hold open
 * for reads, instead of issuing an HTTP POST per drawing burst.
 *
 * <p>Auth, relay storage, and the outbound broadcast are all handled by
 * {@link RoomCommandService#publishWhiteboardSnapshot} — the exact same code path the
 * HTTP fallback ({@code POST /api/rooms/{id}/whiteboard/elements}) uses. This handler
 * only adapts the STOMP frame to that call; the authenticated user comes from the
 * {@link StompPrincipal} the CONNECT interceptor put on the session.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class RoomWhiteboardSocketController {

    private final RoomCommandService commands;

    @MessageMapping("/rooms/{roomId}/whiteboard")
    public void publish(
            @DestinationVariable UUID roomId,
            WhiteboardElementsRequest request,
            Principal principal
    ) {
        if (!(principal instanceof StompPrincipal sp)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        commands.publishWhiteboardSnapshot(
                roomId,
                new ExcalidrawSnapshot(request.elements(), request.appState()),
                sp.userId()
        );
    }

    /**
     * Keep auth/relay failures off the inbound channel: drop and log instead of letting
     * Spring's default handler propagate. The frontend already gates writers, so a
     * rejected SEND is a benign race (e.g. a just-revoked expert-session writer).
     */
    @MessageExceptionHandler(AppException.class)
    public void onError(AppException e) {
        log.debug("[whiteboard-ws] rejected publish: {}", e.getErrorCode());
    }
}
