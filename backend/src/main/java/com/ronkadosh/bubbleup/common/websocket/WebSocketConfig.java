package com.ronkadosh.bubbleup.common.websocket;

import com.ronkadosh.bubbleup.common.config.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<WsChannelInterceptor> inboundInterceptors;
    private final CorsProperties corsProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Server-side STOMP heartbeats (10s in/out) keep an idle connection alive so a
        // reverse proxy's idle-read timeout (nginx default 60s) doesn't tear it down and
        // trigger a reconnect storm. setHeartbeatValue requires a TaskScheduler — the two
        // are a pair; omitting the scheduler throws at startup.
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().split(","));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        List<WsChannelInterceptor> ordered = inboundInterceptors.stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toList();
        registration.interceptors(ordered.toArray(new ChannelInterceptor[0]));
    }
}
