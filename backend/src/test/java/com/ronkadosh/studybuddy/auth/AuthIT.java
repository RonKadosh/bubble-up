package com.ronkadosh.studybuddy.auth;

import com.ronkadosh.studybuddy.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends IntegrationTest {

    @Test
    void register_happy_path_returns_token_and_user() throws Exception {
        String body = "{\"email\":\"new@test.local\",\"password\":\"Passw0rd!\"}";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.userId").exists())
                .andExpect(jsonPath("$.data.email").value("new@test.local"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
    }

    @Test
    void register_duplicate_email_rejected() throws Exception {
        AuthedUser first = registerAndLogin();
        String body = String.format("{\"email\":\"%s\",\"password\":\"Passw0rd!\"}", first.email());
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void login_happy_path_returns_token() throws Exception {
        AuthedUser u = registerAndLogin();
        String body = String.format("{\"email\":\"%s\",\"password\":\"Passw0rd!\"}", u.email());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.userId").value(u.id().toString()));
    }

    @Test
    void login_wrong_password_rejected() throws Exception {
        AuthedUser u = registerAndLogin();
        String body = String.format("{\"email\":\"%s\",\"password\":\"wrong\"}", u.email());
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }
}
