package com.ronkadosh.bubbleup.common.ratelimit;

import com.ronkadosh.bubbleup.common.config.RateLimitProperties;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Enforces {@link RateLimit} annotations on controller handler methods. Runs after
 * Spring Security and handler mapping, so it sees the {@link HandlerMethod} (to read
 * the annotation) and the URI template variables (for per-group/per-file/per-room
 * scoping). Rejections throw {@link AppException}, which flows through the existing
 * {@code GlobalExceptionHandler} → standard envelope + HTTP 429.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit[] limits = method.getMethodAnnotation(RateLimits.class) != null
                ? method.getMethodAnnotation(RateLimits.class).value()
                : single(method.getMethodAnnotation(RateLimit.class));
        if (limits.length == 0) {
            return true;
        }

        String handlerId = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
        for (RateLimit limit : limits) {
            String key = handlerId + "|" + limit.scope() + "|" + dimension(limit.scope(), request);
            if (!rateLimiter.tryAcquire(key, limit.limit(), limit.windowSeconds())) {
                response.setHeader(HttpHeaders.RETRY_AFTER,
                        String.valueOf(rateLimiter.retryAfterSeconds(key, limit.windowSeconds())));
                throw new AppException(ErrorCode.TOO_MANY_REQUESTS);
            }
        }
        return true;
    }

    private RateLimit[] single(RateLimit limit) {
        return limit == null ? new RateLimit[0] : new RateLimit[]{limit};
    }

    /** The variable part of the bucket key for a scope (the stable part is the handler id). */
    private String dimension(RateLimitScope scope, HttpServletRequest request) {
        return switch (scope) {
            case PER_USER -> userId();
            case PER_IP -> request.getRemoteAddr();
            case PER_USER_PER_ROOM -> userId() + ":" + firstPathVar(request, "roomId", "id");
            case PER_USER_PER_GROUP -> userId() + ":" + firstPathVar(request, "groupId", "id");
            case PER_GROUP -> firstPathVar(request, "groupId", "id");
            case PER_FILE -> firstPathVar(request, "fileId");
        };
    }

    private String userId() {
        return currentUserProvider.get().id().toString();
    }

    @SuppressWarnings("unchecked")
    private String firstPathVar(HttpServletRequest request, String... names) {
        Map<String, String> vars = (Map<String, String>)
                request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars != null) {
            for (String name : names) {
                String value = vars.get(name);
                if (value != null) {
                    return value;
                }
            }
        }
        return "?";
    }
}
