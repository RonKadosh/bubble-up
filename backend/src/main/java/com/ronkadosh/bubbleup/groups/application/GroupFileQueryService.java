package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.groups.api.dto.GroupFileResponse;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.model.GroupFile;
import com.ronkadosh.bubbleup.groups.persistence.GroupFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupFileQueryService {

    private final GroupFileRepository repo;
    private final GroupInternalService groupInternalService;
    private final FileStorageService fileStorageService;

    /**
     * @param folderScope ROOT = files at the root (folderId IS NULL),
     *                    FOLDER = files inside the given folderId,
     *                    ALL = every file in the group (used by the agenda view).
     */
    @Transactional(readOnly = true)
    public List<GroupFileResponse> list(UUID groupId, UUID requesterId, FolderScope folderScope, UUID folderId) {
        requireGroupAndMembership(groupId, requesterId);
        List<GroupFile> rows = switch (folderScope) {
            case ALL -> repo.findAllByGroupId(groupId);
            case ROOT -> repo.findAllByGroupIdAndFolderIdIsNull(groupId);
            case FOLDER -> repo.findAllByGroupIdAndFolderId(groupId, folderId);
        };
        return rows.stream().map(GroupFileResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public GroupFileResponse findOne(UUID groupId, UUID groupFileId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        GroupFile row = repo.findByIdAndGroupId(groupFileId, groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_FILE_NOT_FOUND));
        return GroupFileResponse.from(row);
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(UUID groupId, UUID groupFileId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        GroupFile row = repo.findByIdAndGroupId(groupFileId, groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_FILE_NOT_FOUND));
        byte[] bytes = fileStorageService.download(row.getFileId());
        return new DownloadedFile(bytes, row.getContentType(), row.getOriginalName(), row.getSizeBytes());
    }

    public enum FolderScope { ALL, ROOT, FOLDER }

    private void requireGroupAndMembership(UUID groupId, UUID userId) {
        if (!groupInternalService.groupExists(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isMember(groupId, userId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }
}
