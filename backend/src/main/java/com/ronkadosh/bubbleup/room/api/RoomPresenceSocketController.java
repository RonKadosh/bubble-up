package com.ronkadosh.bubbleup.room.api;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.StompPrincipal;
import com.ronkadosh.bubbleup.room.api.dto.RoomPresenceReportRequest;
import com.ronkadosh.bubbleup.room.application.RoomCallPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Inbound STOMP path for video-call presence. Clients SEND a JOIN/LEAVE to
 * {@code /app/rooms/{roomId}/presence} off Jitsi's conference events. Membership
 * is enforced inside {@link RoomCallPresenceService}; the authenticated user comes
 * from the {@link StompPrincipal} the CONNECT interceptor put on the session.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class RoomPresenceSocketController {

    private final RoomCallPresenceService presence;

    @MessageMapping("/rooms/{roomId}/presence")
    public void report(
            @DestinationVariable UUID roomId,
            RoomPresenceReportRequest request,
            Principal principal
    ) {
        if (!(principal instanceof StompPrincipal sp)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if ("JOIN".equals(request.event())) {
            presence.join(roomId, sp.userId());
        } else if ("LEAVE".equals(request.event())) {
            presence.leave(roomId, sp.userId());
        }
    }

    @MessageExceptionHandler(AppException.class)
    public void onError(AppException e) {
        log.debug("[presence-ws] rejected report: {}", e.getErrorCode());
    }
}
