package com.ronkadosh.bubbleup.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Report Center: submit (+ optional image), admin inbox listing/decision, and counts. */
class ReportIT extends IntegrationTest {

    private String submitReport(AuthedUser reporter, String subject) throws Exception {
        String body = String.format(
                "{\"category\":\"ABUSE\",\"subject\":\"%s\",\"description\":\"Someone is being abusive in chat.\"}",
                subject);
        String json = mvc.perform(post("/api/reports")
                        .with(bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.hasAttachment").value(false))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("data").get("id").asText();
    }

    @Test
    void submit_attach_image_then_admin_lists_and_resolves() throws Exception {
        AuthedUser reporter = registerAndLogin();
        AuthedUser admin = registerAndLoginAsAdmin();

        String reportId = submitReport(reporter, "Abuse " + java.util.UUID.randomUUID());

        // Attach a screenshot.
        MockMultipartFile image = new MockMultipartFile(
                "file", "shot.png", "image/png", new byte[]{1, 2, 3, 4});
        mvc.perform(multipart("/api/reports/" + reportId + "/image")
                        .file(image)
                        .with(bearer(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasAttachment").value(true));

        // Admin sees it in the PENDING inbox.
        mvc.perform(get("/api/admin/reports?status=PENDING").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + reportId + "')]").exists());

        // Attachment streams back.
        mvc.perform(get("/api/admin/reports/" + reportId + "/image").with(bearer(admin)))
                .andExpect(status().isOk());

        // Counts reflect the pending report.
        String counts = mvc.perform(get("/api/admin/inbox/counts").with(bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(counts).get("data");
        org.assertj.core.api.Assertions.assertThat(data.get("pendingReports").asLong()).isGreaterThanOrEqualTo(1);

        // Resolve it → leaves the PENDING list.
        mvc.perform(post("/api/admin/reports/" + reportId + "/resolve")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Warned the user.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        mvc.perform(get("/api/admin/reports?status=PENDING").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + reportId + "')]").doesNotExist());
    }

    @Test
    void non_admin_cannot_list_reports() throws Exception {
        AuthedUser user = registerAndLogin();
        mvc.perform(get("/api/admin/reports").with(bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannot_attach_unsupported_image_type() throws Exception {
        AuthedUser reporter = registerAndLogin();
        String reportId = submitReport(reporter, "Bad type " + java.util.UUID.randomUUID());

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/reports/" + reportId + "/image")
                        .file(pdf)
                        .with(bearer(reporter)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_IMAGE_TYPE_NOT_ALLOWED"));
    }
}
