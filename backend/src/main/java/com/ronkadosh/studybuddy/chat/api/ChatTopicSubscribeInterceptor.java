package com.ronkadosh.studybuddy.chat.api;

import com.ronkadosh.studybuddy.chat.internal.ChatInternalService;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.websocket.StompPrincipal;
import com.ronkadosh.studybuddy.common.websocket.WsChannelInterceptor;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
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
 * Gates STOMP SUBSCRIBE frames on /topic/chat/{roomId}: caller must be a member
 * of the room's group. Runs after StompAuthChannelInterceptor populated the
 * Principal on CONNECT.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class ChatTopicSubscribeInterceptor implements WsChannelInterceptor {

    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/([0-9a-fA-F-]{36})$");

    private final ChatInternalService chatInternalService;
    private final GroupInternalService groupInternalService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) return message;

        Matcher m = CHAT_TOPIC.matcher(destination);
        if (!m.matches()) return message;

        UUID roomId = UUID.fromString(m.group(1));
        UUID userId = userIdFrom(accessor.getUser());
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID groupId = chatInternalService.getGroupIdForRoom(roomId);
        if (groupId == null || !groupInternalService.isMember(groupId, userId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        return message;
    }

    private UUID userIdFrom(Principal principal) {
        if (principal instanceof StompPrincipal sp) return sp.userId();
        return null;
    }
}
