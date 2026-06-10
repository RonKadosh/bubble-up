package com.ronkadosh.bubbleup.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.api.ErrorResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Block unverified Google-signup sessions from using the app until the user
 * clicks Bubble.up's verification link.
 *
 * <p>The temporary password/JWT testing flow is exempt because those accounts
 * do not have a Google identity attached (`googleSub == null`).</p>
 */
@Component
public class EmailVerificationGateFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";
    private static final String VERIFY_PREFIX = "/api/auth/verify-email/";
    private static final String REFRESH_PATH = "/api/auth/refresh";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public EmailVerificationGateFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CurrentUser currentUser)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> userOpt = userRepository.findById(currentUser.id());
        if (userOpt.isEmpty()) {
            SecurityContextHolder.clearContext();
            writeFailure(response, ErrorCode.UNAUTHORIZED, "Your sign-in session is no longer valid. Please sign in again.");
            return;
        }

        User user = userOpt.get();
        boolean requiresVerification = user.getGoogleSub() != null && !user.isEmailVerified();
        if (requiresVerification && !isAllowedForUnverified(path)) {
            writeFailure(
                    response,
                    ErrorCode.EMAIL_VERIFICATION_REQUIRED,
                    "Check your academic inbox and finish Bubble.up email verification before using the app."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAllowedForUnverified(String path) {
        return path.startsWith(VERIFY_PREFIX)
                || REFRESH_PATH.equals(path)
                || LOGOUT_PATH.equals(path);
    }

    private void writeFailure(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponse error = new ErrorResponse(code.name(), message, code.getCategory().name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(error));
    }
}
