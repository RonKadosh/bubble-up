package com.ronkadosh.bubbleup.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RequestContextFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_USER_ID = "userId";
    private static final Logger log = LoggerFactory.getLogger(RequestContextFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            CurrentUser currentUser = null;
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof CurrentUser cu) {
                currentUser = cu;
            }

            MDC.put(MDC_TRACE_ID, traceId);
            if (currentUser != null) {
                MDC.put(MDC_USER_ID, currentUser.id().toString());
            }
            RequestContext.set(new RequestContext(currentUser, traceId));
            filterChain.doFilter(request, response);
        } finally {
            logRequest(request, response, started);
            RequestContext.clear();
            MDC.remove(MDC_USER_ID);
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long started) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        int status = response.getStatus();
        String userId = Optional.ofNullable(RequestContext.current())
                .map(RequestContext::currentUser)
                .map(CurrentUser::id)
                .map(UUID::toString)
                .orElse("-");

        log.info("http_request method={} path={} status={} durationMs={} userId={} ip={} userAgent={}",
                request.getMethod(),
                request.getRequestURI(),
                status,
                durationMs,
                userId,
                clientIp(request),
                singleLine(request.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return singleLine(forwardedFor.split(",")[0].trim());
        }
        return singleLine(request.getRemoteAddr());
    }

    private String singleLine(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value.replaceAll("[\\r\\n\\t]+", " ");
    }
}
