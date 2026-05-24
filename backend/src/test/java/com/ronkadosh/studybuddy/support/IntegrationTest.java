package com.ronkadosh.studybuddy.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    protected AuthedUser registerAndLogin() throws Exception {
        String email = "u" + UUID.randomUUID() + "@test.local";
        String body = String.format("{\"email\":\"%s\",\"password\":\"Passw0rd!\"}", email);
        String json = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        return new AuthedUser(
                UUID.fromString(data.get("userId").asText()),
                email,
                data.get("accessToken").asText(),
                data.get("refreshToken").asText()
        );
    }

    protected RequestPostProcessor bearer(AuthedUser u) {
        return req -> {
            req.addHeader("Authorization", "Bearer " + u.jwt());
            return req;
        };
    }

    /** `jwt` is the access JWT (kept short to minimize churn across existing tests). */
    public record AuthedUser(UUID id, String email, String jwt, String refreshToken) {}
}
