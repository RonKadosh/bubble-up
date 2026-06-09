package com.ronkadosh.bubbleup.auth.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Catches OAuth2 failures that happen <em>before</em> our own success handler
 * runs — e.g. the user clicked "Cancel" on the Google consent screen, or
 * Spring couldn't verify the ID token signature, or the OAuth client is
 * misconfigured. Redirects to the same frontend callback page with an
 * {@code error} query parameter, mirroring the success handler's shape.
 */
@Component
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        log.info("OAuth2 login failed at Spring layer: {}", exception.getMessage());
        String first = corsAllowedOrigins.split(",")[0].trim();
        if (first.endsWith("/")) first = first.substring(0, first.length() - 1);
        response.sendRedirect(first + "/auth/callback?error="
                + URLEncoder.encode("OAUTH_FAILED", StandardCharsets.UTF_8));
    }
}
