package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileAccessPolicy;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.common.file.FileUploadRequest;
import com.ronkadosh.bubbleup.common.file.StoredFile;
import com.ronkadosh.bubbleup.groups.api.dto.GroupFileResponse;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.model.GroupFile;
import com.ronkadosh.bubbleup.groups.persistence.GroupFileRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final GroupFolderRepository folderRepo;
    private final GroupInternalService groupInternalService;
    private final FileStorageService fileStorageService;
    private final GroupFileTypeFilter typeFilter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public GroupFileResponse upload(UUID groupId, UUID uploaderId, MultipartFile file, UUID folderId) {
        requireGroupAndMembership(groupId, uploaderId);
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        typeFilter.requireAllowed(file.getOriginalFilename(), file.getContentType());
        if (folderId != null) {
            folderRepo.findByIdAndGroupId(folderId, groupId)
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        }

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
                .folderId(folderId)
                .fileId(stored.fileId())
                .originalName(file.getOriginalFilename())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .sizeBytes(file.getSize())
                .build());
        eventPublisher.publishEvent(new UserBehaviorEvent(uploaderId, BehaviorEventType.UPLOADED_FILE));
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
