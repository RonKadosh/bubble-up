package com.ronkadosh.studybuddy.auth;

import com.ronkadosh.studybuddy.auth.model.RefreshToken;
import com.ronkadosh.studybuddy.auth.persistence.RefreshTokenRepository;
import com.ronkadosh.studybuddy.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthRefreshIT extends IntegrationTest {

    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    void refresh_happy_returns_new_pair_and_revokes_old() throws Exception {
        AuthedUser u = registerAndLogin();
        String oldRefresh = u.refreshToken();

        String json = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andReturn().getResponse().getContentAsString();

        String newRefresh = om.readTree(json).get("data").get("refreshToken").asText();
        assertNotEquals(oldRefresh, newRefresh);

        // Re-using the old refresh now → REUSED
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void reuse_revokes_entire_chain() throws Exception {
        AuthedUser u = registerAndLogin();
        String tokenA = u.refreshToken();

        // Rotate A → B
        String tokenB = rotate(tokenA);
        // Rotate B → C
        String tokenC = rotate(tokenB);

        // Attacker presents old A → triggers chain revocation
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenA + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));

        // Now C (the legitimate live token) is also dead — chain was revoked
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenC + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void refresh_with_random_token_returns_INVALID() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"definitely-not-a-real-token-aaaaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refresh_with_expired_token_returns_EXPIRED() throws Exception {
        AuthedUser u = registerAndLogin();
        // Manually expire the freshly-issued refresh row.
        Optional<RefreshToken> rowOpt = refreshTokenRepository.findAll().stream()
                .filter(r -> r.getUserId().equals(u.id()) && r.getRevokedAt() == null)
                .findFirst();
        assertTrue(rowOpt.isPresent(), "refresh row should exist for user");
        RefreshToken row = rowOpt.get();
        // Hack the expiry to the past via a fresh entity that overwrites the column
        RefreshToken expired = RefreshToken.builder()
                .id(row.getId())
                .tokenHash(row.getTokenHash())
                .userId(row.getUserId())
                .expiresAt(Instant.now().minusSeconds(60))
                .rotatedFromId(row.getRotatedFromId())
                .revokedAt(null)
                .createdAt(row.getCreatedAt())
                .build();
        refreshTokenRepository.save(expired);

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + u.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void logout_revokes_refresh_subsequent_refresh_rejected() throws Exception {
        AuthedUser u = registerAndLogin();

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + u.refreshToken() + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + u.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void logout_with_unknown_token_returns_ok_silently() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"unknown-token-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isOk());
    }

    private String rotate(String oldRefresh) throws Exception {
        String json = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("data").get("refreshToken").asText();
    }
}
