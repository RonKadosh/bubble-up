package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupFolderIT extends IntegrationTest {

    @Test
    void member_can_create_and_list_folders() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lectures\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Lectures"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist());

        mvc.perform(get("/api/groups/{id}/folders", groupId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Lectures"));
    }

    @Test
    void create_folder_duplicate_sibling_name_409() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        createFolder(owner, groupId, "Lectures", null);

        mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lectures\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOLDER_NAME_TAKEN"));
    }

    @Test
    void create_folder_invalid_name_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad/slash\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FOLDER_NAME_INVALID"));
    }

    @Test
    void create_subfolder_inside_parent() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID parentId = createFolder(owner, groupId, "Year 1", null);

        mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Week 1\",\"parentId\":\"" + parentId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentId").value(parentId.toString()));
    }

    @Test
    void upload_to_folder_succeeds() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID folderId = createFolder(owner, groupId, "Lectures", null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "x".getBytes());

        mvc.perform(multipart("/api/groups/{id}/files", groupId)
                        .file(file)
                        .param("folderId", folderId.toString())
                        .with(bearer(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.folderId").value(folderId.toString()));

        // list with ?folderId= filters to that folder
        mvc.perform(get("/api/groups/{id}/files", groupId)
                        .param("folderId", folderId.toString())
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalName").value("notes.txt"));

        // list with ?scope=root excludes folder contents
        mvc.perform(get("/api/groups/{id}/files", groupId)
                        .param("scope", "root")
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void upload_to_folder_from_other_group_404() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID otherGroupId = createGroup(owner);
        UUID foreignFolderId = createFolder(owner, otherGroupId, "Lectures", null);

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "x".getBytes());
        mvc.perform(multipart("/api/groups/{id}/files", groupId)
                        .file(file)
                        .param("folderId", foreignFolderId.toString())
                        .with(bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FOLDER_NOT_FOUND"));
    }

    @Test
    void delete_empty_folder_succeeds() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID folderId = createFolder(owner, groupId, "ToDelete", null);

        mvc.perform(delete("/api/groups/{id}/folders/{fid}", groupId, folderId).with(bearer(owner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/groups/{id}/folders", groupId).with(bearer(owner)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void delete_folder_with_file_409_NOT_EMPTY() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID folderId = createFolder(owner, groupId, "Lectures", null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", "x".getBytes());
        mvc.perform(multipart("/api/groups/{id}/files", groupId)
                        .file(file)
                        .param("folderId", folderId.toString())
                        .with(bearer(owner)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/groups/{id}/folders/{fid}", groupId, folderId).with(bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOLDER_NOT_EMPTY"));
    }

    @Test
    void delete_folder_with_subfolder_409_NOT_EMPTY() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID parentId = createFolder(owner, groupId, "Year 1", null);
        createFolder(owner, groupId, "Week 1", parentId);

        mvc.perform(delete("/api/groups/{id}/folders/{fid}", groupId, parentId).with(bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FOLDER_NOT_EMPTY"));
    }

    @Test
    void non_creator_non_owner_cannot_delete() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser member = registerAndLogin();
        UUID groupId = createGroup(owner);
        joinGroup(member, groupId);
        UUID folderId = createFolder(member, groupId, "Mine", null);
        AuthedUser other = registerAndLogin();
        joinGroup(other, groupId);

        mvc.perform(delete("/api/groups/{id}/folders/{fid}", groupId, folderId).with(bearer(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_FOLDER_CREATOR_OR_GROUP_OWNER"));
    }

    @Test
    void non_member_cannot_list_or_create() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(get("/api/groups/{id}/folders", groupId).with(bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));

        mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void download_inline_disposition_uses_inline_header() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID fileId = uploadFile(owner, groupId, "doc.pdf", "application/pdf", "%PDF".getBytes());

        mvc.perform(get("/api/groups/{id}/files/{fid}/download", groupId, fileId)
                        .param("disposition", "inline")
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"doc.pdf\""));
    }

    @Test
    void file_metadata_one_shot_endpoint() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID fileId = uploadFile(owner, groupId, "n.txt", "text/plain", "x".getBytes());

        mvc.perform(get("/api/groups/{id}/files/{fid}", groupId, fileId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fileId.toString()))
                .andExpect(jsonPath("$.data.originalName").value("n.txt"));
    }

    // ---------- helpers ----------

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private void joinGroup(AuthedUser u, UUID groupId) throws Exception {
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(u)))
                .andExpect(status().isOk());
    }

    private UUID createFolder(AuthedUser u, UUID groupId, String name, UUID parentId) throws Exception {
        String body = parentId == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"parentId\":\"" + parentId + "\"}";
        String json = mvc.perform(post("/api/groups/{id}/folders", groupId)
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private UUID uploadFile(AuthedUser u, UUID groupId, String name, String contentType, byte[] body) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, contentType, body);
        String json = mvc.perform(multipart("/api/groups/{id}/files", groupId).file(file).with(bearer(u)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}
