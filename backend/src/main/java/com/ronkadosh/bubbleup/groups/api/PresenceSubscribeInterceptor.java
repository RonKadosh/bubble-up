package com.ronkadosh.bubbleup.groups.api;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.StompPrincipal;
import com.ronkadosh.bubbleup.common.websocket.WsChannelInterceptor;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
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
 * Gates STOMP SUBSCRIBE frames on /topic/presence/{groupId}: caller must be a member
 * of the group. Mirrors {@link com.ronkadosh.bubbleup.chat.api.ChatTopicSubscribeInterceptor}.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class PresenceSubscribeInterceptor implements WsChannelInterceptor {

    private static final Pattern PRESENCE_TOPIC = Pattern.compile("^/topic/presence/([0-9a-fA-F-]{36})$");

    private final GroupInternalService groupInternalService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) return message;

        Matcher m = PRESENCE_TOPIC.matcher(destination);
        if (!m.matches()) return message;

        UUID groupId = UUID.fromString(m.group(1));
        UUID userId = userIdFrom(accessor.getUser());
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (!groupInternalService.isMember(groupId, userId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        return message;
    }

    private UUID userIdFrom(Principal principal) {
        if (principal instanceof StompPrincipal sp) return sp.userId();
        return null;
    }
}
