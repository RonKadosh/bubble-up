package com.ronkadosh.bubbleup.groups.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.groups.api.dto.GroupFileResponse;
import com.ronkadosh.bubbleup.groups.application.DownloadedFile;
import com.ronkadosh.bubbleup.groups.application.GroupFileCommandService;
import com.ronkadosh.bubbleup.groups.application.GroupFileQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.GROUPS_BASE + "/{groupId}/files")
@RequiredArgsConstructor
public class GroupFileController {

    private final GroupFileCommandService commands;
    private final GroupFileQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupFileResponse> upload(
            @PathVariable UUID groupId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID folderId
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.upload(groupId, me, file, folderId));
    }

    /**
     * List files in the group. By default returns every file (powers the agenda /
     * link-picker views). When {@code folderId} is supplied, returns only files in
     * that folder. When {@code scope=root} is supplied, returns only files at the
     * root (folderId IS NULL). {@code folderId} wins when both are present.
     */
    @GetMapping
    public ApiResponse<List<GroupFileResponse>> list(
            @PathVariable UUID groupId,
            @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) String scope
    ) {
        UUID me = currentUserProvider.get().id();
        GroupFileQueryService.FolderScope folderScope;
        if (folderId != null) {
            folderScope = GroupFileQueryService.FolderScope.FOLDER;
        } else if ("root".equalsIgnoreCase(scope)) {
            folderScope = GroupFileQueryService.FolderScope.ROOT;
        } else {
            folderScope = GroupFileQueryService.FolderScope.ALL;
        }
        return ApiResponse.success(queries.list(groupId, me, folderScope, folderId));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<GroupFileResponse> findOne(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.findOne(groupId, fileId, me));
    }

    /**
     * Returns the raw bytes. Documented exception to "always return ApiResponse" —
     * binary endpoints need ResponseEntity&lt;Resource&gt; with proper headers.
     *
     * <p>{@code disposition=inline} switches the Content-Disposition header from
     * {@code attachment} to {@code inline}, so browsers render the file in-place
     * (used by the in-app PDF / image viewer). Default behavior unchanged.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId,
            @RequestParam(required = false) String disposition
    ) {
        UUID me = currentUserProvider.get().id();
        DownloadedFile dl = queries.download(groupId, fileId, me);
        String dispositionType = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        return ResponseEntity.ok()
                .contentType(parseContentType(dl.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        dispositionType + "; filename=\"" + sanitizeFilename(dl.originalName()) + "\"")
                .contentLength(dl.sizeBytes())
                .body(new ByteArrayResource(dl.bytes()));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId
    ) {
        UUID me = currentUserProvider.get().id();
        commands.delete(groupId, fileId, me);
        return ApiResponse.ok();
    }

    private static MediaType parseContentType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "download";
        return name.replaceAll("[\\r\\n\"]", "_");
    }
}
