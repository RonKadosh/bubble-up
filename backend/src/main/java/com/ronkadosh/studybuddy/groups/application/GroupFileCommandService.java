package com.ronkadosh.studybuddy.groups.application;

import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.file.FileAccessPolicy;
import com.ronkadosh.studybuddy.common.file.FileStorageService;
import com.ronkadosh.studybuddy.common.file.FileUploadRequest;
import com.ronkadosh.studybuddy.common.file.StoredFile;
import com.ronkadosh.studybuddy.groups.api.dto.GroupFileResponse;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import com.ronkadosh.studybuddy.groups.model.GroupFile;
import com.ronkadosh.studybuddy.groups.persistence.GroupFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupFileCommandService {

    static final long MAX_BYTES = 25L * 1024 * 1024;

    private final GroupFileRepository repo;
    private final GroupInternalService groupInternalService;
    private final FileStorageService fileStorageService;
    private final GroupFileTypeFilter typeFilter;

    @Transactional
    public GroupFileResponse upload(UUID groupId, UUID uploaderId, MultipartFile file) {
        requireGroupAndMembership(groupId, uploaderId);
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        typeFilter.requireAllowed(file.getOriginalFilename(), file.getContentType());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to read upload");
        }

        StoredFile stored = fileStorageService.upload(new FileUploadRequest(
                file.getOriginalFilename(),
                file.getContentType(),
                bytes,
                FileAccessPolicy.GROUP
        ));

        GroupFile row = repo.save(GroupFile.builder()
                .groupId(groupId)
                .uploaderId(uploaderId)
                .fileId(stored.fileId())
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .sizeBytes(file.getSize())
                .build());
        return GroupFileResponse.from(row);
    }

    @Transactional
    public void delete(UUID groupId, UUID groupFileId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        GroupFile row = repo.findByIdAndGroupId(groupFileId, groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_FILE_NOT_FOUND));
        if (!row.getUploaderId().equals(requesterId)
                && !groupInternalService.isOwner(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_FILE_UPLOADER_OR_GROUP_OWNER);
        }
        fileStorageService.delete(row.getFileId());
        repo.delete(row);
    }

    private void requireGroupAndMembership(UUID groupId, UUID userId) {
        if (!groupInternalService.groupExists(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isMember(groupId, userId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }
}
