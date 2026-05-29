package com.ronkadosh.bubbleup.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class RequestContextFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            CurrentUser currentUser = null;
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof CurrentUser cu) {
                currentUser = cu;
            }
            RequestContext.set(new RequestContext(currentUser, traceId));
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
