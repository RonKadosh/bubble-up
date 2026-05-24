package com.ronkadosh.studybuddy.groups.api;

import com.ronkadosh.studybuddy.common.api.ApiPaths;
import com.ronkadosh.studybuddy.common.api.ApiResponse;
import com.ronkadosh.studybuddy.common.context.CurrentUserProvider;
import com.ronkadosh.studybuddy.groups.api.dto.GroupFileResponse;
import com.ronkadosh.studybuddy.groups.application.DownloadedFile;
import com.ronkadosh.studybuddy.groups.application.GroupFileCommandService;
import com.ronkadosh.studybuddy.groups.application.GroupFileQueryService;
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
            @RequestPart("file") MultipartFile file
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.upload(groupId, me, file));
    }

    @GetMapping
    public ApiResponse<List<GroupFileResponse>> list(@PathVariable UUID groupId) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.list(groupId, me));
    }

    /**
     * Returns the raw bytes. Documented exception to "always return ApiResponse" —
     * binary endpoints need ResponseEntity<Resource> with proper headers.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID groupId,
            @PathVariable UUID fileId
    ) {
        UUID me = currentUserProvider.get().id();
        DownloadedFile dl = queries.download(groupId, fileId, me);
        return ResponseEntity.ok()
                .contentType(parseContentType(dl.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sanitizeFilename(dl.originalName()) + "\"")
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
