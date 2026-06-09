package com.ronkadosh.bubbleup.help;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.help.persistence.HelpQuestionEntryRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HelpControllerIT extends IntegrationTest {

    @Autowired
    HelpQuestionEntryRepository questionEntries;

    @Test
    void topics_require_authentication() throws Exception {
        mvc.perform(get("/api/help/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void topics_search_safe_student_catalog() throws Exception {
        AuthedUser user = registerAndLogin();

        String json = mvc.perform(get("/api/help/topics")
                        .param("q", "join bubble")
                        .with(bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode topics = om.readTree(json).get("data");
        assertThat(topics).isNotEmpty();
        assertThat(ids(topics)).contains("join-bubble");
        assertThat(ids(topics)).doesNotContain("expert-hub");
    }

    @Test
    void ask_uses_local_catalog_when_ai_is_disabled() throws Exception {
        AuthedUser user = registerAndLogin();

        String json = mvc.perform(post("/api/help/ask")
                        .with(bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "How do I enroll in a course?",
                                  "locale": "en",
                                  "currentPath": "/dashboard"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = om.readTree(json).get("data");
        assertThat(data.get("source").asText()).isEqualTo("LOCAL");
        assertThat(data.get("answer").asText()).contains("Courses are the base");
        assertThat(ids(data.get("topics"))).contains("enroll-course");
        assertThat(data.get("actions")).isNotEmpty();
    }

    @Test
    void ask_known_question_uses_local_catalog_without_ai() throws Exception {
        AuthedUser user = registerAndLogin();

        String json = mvc.perform(post("/api/help/ask")
                        .with(bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "join bubble",
                                  "locale": "en",
                                  "currentPath": "/help"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = om.readTree(json).get("data");
        assertThat(data.get("source").asText()).isEqualTo("LOCAL");
        assertThat(ids(data.get("topics"))).contains("join-bubble");
    }

    @Test
    void ask_locked_beginning_question_uses_onboarding_help() throws Exception {
        AuthedUser user = registerAndLogin();

        String json = mvc.perform(post("/api/help/ask")
                        .with(bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "why the system is locked at the beginning?",
                                  "locale": "en",
                                  "currentPath": "/dashboard"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = om.readTree(json).get("data");
        assertThat(data.get("source").asText()).isEqualTo("LOCAL");
        assertThat(data.get("answer").asText()).contains("locked");
        assertThat(ids(data.get("topics"))).contains("complete-onboarding");
    }

    @Test
    void ask_saves_question_entry_and_recent_questions_returns_it() throws Exception {
        AuthedUser user = registerAndLogin();

        mvc.perform(post("/api/help/ask")
                        .with(bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "join bubble",
                                  "locale": "en",
                                  "currentPath": "/help"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(questionEntries.findByUserIdOrderByCreatedAtDesc(
                user.id(),
                org.springframework.data.domain.PageRequest.of(0, 5)
        )).isNotEmpty();

        String json = mvc.perform(get("/api/help/questions")
                        .with(bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode questions = om.readTree(json).get("data");
        assertThat(questions).isNotEmpty();
        assertThat(questions.get(0).get("question").asText()).isEqualTo("join bubble");
        assertThat(questions.get(0).get("source").asText()).isEqualTo("LOCAL");
    }

    private static java.util.List<String> ids(JsonNode topics) {
        java.util.List<String> out = new java.util.ArrayList<>();
        topics.forEach(topic -> out.add(topic.get("id").asText()));
        return out;
    }
}
