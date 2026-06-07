package com.ronkadosh.bubbleup.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §8 (Files panel): create folder, upload into it, browse, download,
 * share a file to chat, delete. Plus the blocked-type guard the upload surfaces.
 */
class FilesJourneyE2EIT extends E2EFlowTest {

    @Test
    void create_folder_upload_browse_download_and_delete() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Files " + unique());

        // "New folder".
        UUID folderId = UUID.fromString(dataOf(mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lecture notes\",\"parentId\":null}"))
                .andExpect(status().isCreated())).get("id").asText());

        // Upload into the folder (FilesPanel passes folderId as a query param).
        MockMultipartFile file = new MockMultipartFile("file", "wk1.txt", "text/plain", "week one".getBytes());
        UUID fileId = UUID.fromString(dataOf(mvc.perform(multipart("/api/groups/{id}/files", groupId)
                        .file(file).param("folderId", folderId.toString()).with(bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalName").value("wk1.txt")))
                .get("id").asText());

        // Browse the folder → the file shows; root scope is empty.
        mvc.perform(get("/api/groups/{id}/files", groupId).param("folderId", folderId.toString())
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(fileId.toString()));
        mvc.perform(get("/api/groups/{id}/files", groupId).param("scope", "root").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // FileLinkCard metadata resolve + download bytes.
        mvc.perform(get("/api/groups/{id}/files/{fid}", groupId, fileId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentType").value("text/plain"));
        var dl = mvc.perform(get("/api/groups/{id}/files/{fid}/download", groupId, fileId).with(bearer(owner)))
                .andExpect(status().isOk()).andReturn();
        assertThat(dl.getResponse().getContentAsByteArray()).isEqualTo("week one".getBytes());

        // Delete file then folder.
        mvc.perform(delete("/api/groups/{id}/files/{fid}", groupId, fileId).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/folders/{fid}", groupId, folderId).with(bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void share_uploaded_file_to_chat_as_link() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Share " + unique());
        UUID roomId = defaultRoomId(owner);

        MockMultipartFile file = new MockMultipartFile("file", "syllabus.txt", "text/plain", "x".getBytes());
        UUID fileId = UUID.fromString(dataOf(mvc.perform(multipart("/api/groups/{id}/files", groupId)
                        .file(file).with(bearer(owner)))
                .andExpect(status().isCreated())).get("id").asText());

        // LinkPickerModal "File" tab → sendLinkMessage(FILE).
        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"LINK\",\"linkTargetType\":\"FILE\",\"linkTargetId\":\"%s\",\"content\":\"the syllabus\"}",
                                fileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.linkTargetType").value("FILE"))
                .andExpect(jsonPath("$.data.linkTargetId").value(fileId.toString()));
    }

    @Test
    void blocked_file_type_is_rejected_on_upload() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Block " + unique());
        MockMultipartFile evil = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "MZ".getBytes());

        mvc.perform(multipart("/api/groups/{id}/files", groupId).file(evil).with(bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_BLOCKED"));
    }

    @Test
    void non_member_cannot_list_files() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser outsider = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Private " + unique());

        mvc.perform(get("/api/groups/{id}/files", groupId).with(bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }
}
