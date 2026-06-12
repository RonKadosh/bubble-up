package com.ronkadosh.bubbleup.auth.api;

import com.ronkadosh.bubbleup.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
@Slf4j
public class OAuth2UnavailableController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String corsAllowedOrigins;

    @GetMapping({
            "/oauth2/authorization/{registrationId}",
            "/login/oauth2/code/{registrationId}"
    })
    public void redirectWhenOAuthIsUnavailable(
            @PathVariable String registrationId,
            HttpServletResponse response
    ) throws IOException {
        if (clientRegistrations.getIfAvailable() != null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        log.info("OAuth2 registration '{}' requested but Google OAuth is not configured", registrationId);
        response.sendRedirect(frontendOrigin() + "/auth/callback?error="
                + URLEncoder.encode(ErrorCode.OAUTH_NOT_CONFIGURED.name(), StandardCharsets.UTF_8));
    }

    private String frontendOrigin() {
        String first = corsAllowedOrigins.split(",")[0].trim();
        return first.endsWith("/") ? first.substring(0, first.length() - 1) : first;
    }
}
