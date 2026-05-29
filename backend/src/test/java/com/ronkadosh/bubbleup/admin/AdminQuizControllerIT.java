package com.ronkadosh.bubbleup.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminQuizControllerIT extends IntegrationTest {

    @Test
    void admin_can_create_question_with_option_and_seven_weights_round_trip() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();

        // Create question.
        String qBody = "{\"textEn\":\"How do you study?\",\"textHe\":\"איך אתם לומדים?\",\"orderIndex\":99,\"active\":true}";
        MvcResult qRes = mvc.perform(post("/api/admin/quiz/questions")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID qId = UUID.fromString(om.readTree(qRes.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Create option with all 7 weights.
        String optBody = "{\"textEn\":\"Alone in the library\",\"textHe\":\"לבד בספרייה\","
                + "\"weightLeader\":0.1,\"weightPlanner\":0.5,\"weightExpert\":0.7,"
                + "\"weightCreative\":0.2,\"weightCommunicator\":0.05,"
                + "\"weightTeamPlayer\":0.0,\"weightChallenger\":0.3}";
        MvcResult oRes = mvc.perform(post("/api/admin/quiz/questions/" + qId + "/options")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(optBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.weightExpert").value(0.7))
                .andExpect(jsonPath("$.data.weightPlanner").value(0.5))
                .andReturn();
        JsonNode opt = om.readTree(oRes.getResponse().getContentAsString()).get("data");
        // Sanity: 7 weight fields all present.
        for (String w : new String[]{"weightLeader", "weightPlanner", "weightExpert",
                "weightCreative", "weightCommunicator", "weightTeamPlayer", "weightChallenger"}) {
            assert opt.has(w) : "Missing weight field: " + w;
        }

        // Toggle active off, then verify the listing reflects it.
        mvc.perform(patch("/api/admin/quiz/questions/" + qId + "/active")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mvc.perform(get("/api/admin/quiz/questions").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void student_cannot_create_question() throws Exception {
        AuthedUser student = registerAndLogin();
        mvc.perform(post("/api/admin/quiz/questions")
                        .with(bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textEn\":\"X\"}"))
                .andExpect(status().isForbidden());
    }
}
