package com.ronkadosh.bubbleup.auth.api;

import com.ronkadosh.bubbleup.auth.api.dto.AuthResponse;
import com.ronkadosh.bubbleup.auth.application.OAuthLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2LoginSuccessHandlerTest {

    @Test
    void success_redirect_includes_tokens_and_user_summary_for_spa_callback() throws Exception {
        OAuthLoginService oauthLoginService = mock(OAuthLoginService.class);
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(oauthLoginService);
        ReflectionTestUtils.setField(handler, "corsAllowedOrigins", "http://localhost:3000,https://bubbleup.online");

        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute("sub")).thenReturn("google-sub-123");
        when(principal.getAttribute("email")).thenReturn("user@post.bgu.ac.il");
        when(principal.getAttribute("email_verified")).thenReturn(Boolean.TRUE);
        when(principal.getAttribute("name")).thenReturn("Test User");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        UUID userId = UUID.randomUUID();
        AuthResponse authResponse = new AuthResponse(
                "access token",
                "refresh token",
                userId,
                "user@post.bgu.ac.il",
                "STUDENT",
                "Test User",
                null,
                true
        );
        when(oauthLoginService.loginOrRegister("google-sub-123", "user@post.bgu.ac.il", true, "Test User"))
                .thenReturn(authResponse);

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        String redirect = response.getRedirectedUrl();
        assertThat(redirect).startsWith("http://localhost:3000/auth/callback#");

        String fragment = redirect.substring(redirect.indexOf('#') + 1);
        Map<String, String> params = Arrays.stream(fragment.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)
                ));

        assertThat(params)
                .containsEntry("accessToken", "access token")
                .containsEntry("refreshToken", "refresh token")
                .containsEntry("userId", userId.toString())
                .containsEntry("email", "user@post.bgu.ac.il")
                .containsEntry("role", "STUDENT")
                .containsEntry("displayName", "Test User")
                .containsEntry("avatarUrl", "")
                .containsEntry("emailVerified", "true");
    }
}
