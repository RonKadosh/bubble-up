package com.ronkadosh.studybuddy.groups;

import com.ronkadosh.studybuddy.common.file.FileStorageService;
import com.ronkadosh.studybuddy.groups.persistence.GroupFileRepository;
import com.ronkadosh.studybuddy.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupFileIT extends IntegrationTest {

    @Autowired GroupFileRepository fileRepository;
    @Autowired FileStorageService fileStorage;

    @Test
    void member_can_upload_and_list() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "study hard".getBytes());

        mvc.perform(multipart("/api/groups/{id}/files", groupId).file(file).with(bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalName").value("notes.txt"))
                .andExpect(jsonPath("$.data.contentType").value("text/plain"))
                .andExpect(jsonPath("$.data.sizeBytes").value(10));

        mvc.perform(get("/api/groups/{id}/files", groupId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalName").value("notes.txt"));
    }

    @Test
    void non_member_cannot_upload_or_list_or_download() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID fileId = uploadFile(owner, groupId, "x.txt", "text/plain", "x".getBytes());

        MockMultipartFile file = new MockMultipartFile("file", "y.txt", "text/plain", "y".getBytes());
        mvc.perform(multipart("/api/groups/{id}/files", groupId).file(file).with(bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));

        mvc.perform(get("/api/groups/{id}/files", groupId).with(bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));

        mvc.perform(get("/api/groups/{id}/files/{fid}/download", groupId, fileId).with(bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blocked_extension_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        MockMultipartFile evil = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", "MZ".getBytes());
        mvc.perform(multipart("/api/groups/{id}/files", groupId).file(evil).with(bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_BLOCKED"));
    }

    @Test
    void blocked_mime_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        MockMultipartFile evil = new MockMultipartFile(
                "file", "harmless.bin", "application/x-msdownload", "MZ".getBytes());
        mvc.perform(multipart("/api/groups/{id}/files", groupId).file(evil).with(bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_BLOCKED"));
    }

    @Test
    void download_returns_bytes_and_headers() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        byte[] content = "hello bytes".getBytes();
        UUID fileId = uploadFile(owner, groupId, "n.txt", "text/plain", content);

        var result = mvc.perform(get("/api/groups/{id}/files/{fid}/download", groupId, fileId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(content, result.getResponse().getContentAsByteArray());
        assertEquals("text/plain", result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("filename=\"n.txt\""));
    }

    @Test
    void uploader_can_delete_own_file() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser member = registerAndLogin();
        UUID groupId = createGroup(owner);
        joinGroup(member, groupId);
        UUID fileId = uploadFile(member, groupId, "mine.txt", "text/plain", "m".getBytes());

        mvc.perform(delete("/api/groups/{id}/files/{fid}", groupId, fileId).with(bearer(member)))
                .andExpect(status().isOk());
    }

    @Test
    void owner_can_delete_anyones_file() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser member = registerAndLogin();
        UUID groupId = createGroup(owner);
        joinGroup(member, groupId);
        UUID fileId = uploadFile(member, groupId, "theirs.txt", "text/plain", "t".getBytes());

        mvc.perform(delete("/api/groups/{id}/files/{fid}", groupId, fileId).with(bearer(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void other_member_cannot_delete_someone_elses_file() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser member1 = registerAndLogin();
        AuthedUser member2 = registerAndLogin();
        UUID groupId = createGroup(owner);
        joinGroup(member1, groupId);
        joinGroup(member2, groupId);
        UUID fileId = uploadFile(member1, groupId, "m1.txt", "text/plain", "1".getBytes());

        mvc.perform(delete("/api/groups/{id}/files/{fid}", groupId, fileId).with(bearer(member2)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_FILE_UPLOADER_OR_GROUP_OWNER"));
    }

    @Test
    void deleting_group_cascades_files() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID fileIdRow = uploadFile(owner, groupId, "x.txt", "text/plain", "x".getBytes());
        String storageId = fileRepository.findById(fileIdRow).orElseThrow().getFileId();

        mvc.perform(delete("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isOk());

        // metadata row gone
        assertTrue(fileRepository.findById(fileIdRow).isEmpty());
        // storage bytes gone (download throws because file is missing)
        assertThrows(RuntimeException.class, () -> fileStorage.download(storageId));
    }

    // ---------- helpers ----------

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private void joinGroup(AuthedUser u, UUID groupId) throws Exception {
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(u)))
                .andExpect(status().isOk());
    }

    private UUID uploadFile(AuthedUser u, UUID groupId, String name, String contentType, byte[] body) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, contentType, body);
        String json = mvc.perform(multipart("/api/groups/{id}/files", groupId).file(file).with(bearer(u)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}
