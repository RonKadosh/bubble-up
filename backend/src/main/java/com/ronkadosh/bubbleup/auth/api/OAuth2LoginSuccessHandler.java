package com.ronkadosh.bubbleup.auth.api;

import com.ronkadosh.bubbleup.auth.api.dto.AuthResponse;
import com.ronkadosh.bubbleup.auth.application.OAuthLoginService;
import com.ronkadosh.bubbleup.common.error.AppException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Invoked by Spring Security after Google has authenticated a user and
 * returned an ID token. We forward the parsed user info to
 * {@link OAuthLoginService}, then redirect the browser to the frontend with
 * our own access + refresh tokens in the URL fragment (so they never hit
 * server logs or the {@code Referer} header on the next navigation).
 *
 * <p>Failure cases are translated into a query-parameter error code on the
 * same frontend route so the React side can render a friendly message —
 * a separate {@code OAuth2LoginFailureHandler} catches earlier errors
 * (e.g. Google declined the consent), but exceptions thrown from inside
 * our own {@code OAuthLoginService} land here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthLoginService oauthLoginService;

    /**
     * Where the browser should land after we hand it tokens. Read from the
     * primary CORS-allowed origin so dev (localhost:3000) and prod
     * (bubbleup.<eip>.nip.io) both work without code changes. If multiple
     * origins are configured, the first one wins.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User principal = (OAuth2User) authentication.getPrincipal();

        String googleSub = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        Boolean emailVerified = principal.getAttribute("email_verified");
        String name = principal.getAttribute("name");

        try {
            AuthResponse tokens = oauthLoginService.loginOrRegister(
                    googleSub,
                    email,
                    Boolean.TRUE.equals(emailVerified),
                    name
            );
            response.sendRedirect(buildSuccessRedirect(tokens));
        } catch (AppException e) {
            log.info("OAuth login rejected: {}", e.getErrorCode());
            response.sendRedirect(buildErrorRedirect(e.getErrorCode().name()));
        } catch (Exception e) {
            log.error("Unexpected OAuth login failure", e);
            response.sendRedirect(buildErrorRedirect("INTERNAL_ERROR"));
        }
    }

    /**
     * Redirect to {@code <frontend>/auth/callback#accessToken=...&refreshToken=...}.
     * The URL fragment is invisible to the server side (browsers strip fragments
     * from {@code Referer}, redirect logs, etc.), so the tokens don't bleed
     * into request logs we don't control.
     */
    private String buildSuccessRedirect(AuthResponse tokens) {
        return frontendOrigin() + "/auth/callback#"
                + "accessToken=" + URLEncoder.encode(tokens.accessToken(), StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(tokens.refreshToken(), StandardCharsets.UTF_8);
    }

    /**
     * Redirect to {@code <frontend>/auth/callback?error=<CODE>}. The frontend
     * has a small lookup table from these codes to user-facing copy
     * (NOT_ACADEMIC_EMAIL -> "Sign-in requires an Israeli academic email",
     * ACCOUNT_BANNED     -> "Your account has been suspended", etc.).
     */
    private String buildErrorRedirect(String errorCode) {
        return frontendOrigin() + "/auth/callback?error="
                + URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
    }

    private String frontendOrigin() {
        // First origin wins. Trim trailing slash so we never produce "//auth/callback".
        String first = corsAllowedOrigins.split(",")[0].trim();
        return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
    }
}
