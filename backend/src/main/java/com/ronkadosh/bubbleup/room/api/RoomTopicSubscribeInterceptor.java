package com.ronkadosh.bubbleup.room.api;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.StompPrincipal;
import com.ronkadosh.bubbleup.common.websocket.WsChannelInterceptor;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import com.ronkadosh.bubbleup.room.internal.dto.RoomSummary;
import com.ronkadosh.bubbleup.room.model.RoomScope;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gates STOMP SUBSCRIBE on /topic/rooms/{roomId}/{whiteboard|presence|lifecycle}.
 * Mirrors the access rules used by the REST and chat-WS paths:
 *   GROUP scope         → caller must be a bubble member.
 *   EXPERT_SESSION scope → caller must be the host or a member of an enrolled group
 *                          (see {@link ExpertInternalService#isAuthorizedForSession}).
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class RoomTopicSubscribeInterceptor implements WsChannelInterceptor {

    private static final Pattern ROOM_TOPIC =
            Pattern.compile("^/topic/rooms/([0-9a-fA-F-]{36})/(whiteboard|presence|lifecycle)$");

    private final RoomInternalService roomInternalService;
    private final GroupInternalService groupInternalService;
    private final ExpertInternalService expertInternalService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) return message;

        Matcher m = ROOM_TOPIC.matcher(destination);
        if (!m.matches()) return message;

        UUID roomId = UUID.fromString(m.group(1));
        UUID userId = userIdFrom(accessor.getUser());
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        RoomSummary room = roomInternalService.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        if (room.scope() == RoomScope.GROUP) {
            UUID groupId = room.groupId();
            if (groupId == null || !groupInternalService.isMember(groupId, userId)) {
                throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
            }
        } else if (room.scope() == RoomScope.EXPERT_SESSION) {
            UUID sessionId = room.expertSessionId();
            if (sessionId == null || !expertInternalService.isAuthorizedForSession(sessionId, userId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        } else {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return message;
    }

    private UUID userIdFrom(Principal principal) {
        if (principal instanceof StompPrincipal sp) return sp.userId();
        return null;
    }
}
