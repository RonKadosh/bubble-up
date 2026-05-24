package com.ronkadosh.studybuddy.common.websocket;

import com.ronkadosh.studybuddy.common.context.UserRole;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.security.JwtService;
import com.ronkadosh.studybuddy.common.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Order(1)
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements WsChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String header = accessor.getFirstNativeHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        String token = header.substring(SecurityConstants.BEARER_PREFIX.length());
        if (!jwtService.isValid(token)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        UUID userId = jwtService.extractUserId(token);
        UserRole role = jwtService.extractRole(token);
        accessor.setUser(new StompPrincipal(userId));
        accessor.setHeader("userRole", role);
        return message;
    }
}
