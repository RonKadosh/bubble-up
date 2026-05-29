package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.groups.api.dto.GroupFolderResponse;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.model.GroupFolder;
import com.ronkadosh.bubbleup.groups.persistence.GroupFileRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GroupFolderCommandService {

    /** Reject path separators and control chars. Visible printable + spaces only. */
    private static final Pattern INVALID_NAME = Pattern.compile(".*[/\\\\\\x00-\\x1f].*");

    private final GroupFolderRepository folderRepo;
    private final GroupFileRepository fileRepo;
    private final GroupInternalService groupInternalService;

    @Transactional
    public GroupFolderResponse create(UUID groupId, UUID requesterId, String rawName, UUID parentId) {
        requireGroupAndMembership(groupId, requesterId);
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > 120 || INVALID_NAME.matcher(name).matches()) {
            throw new AppException(ErrorCode.FOLDER_NAME_INVALID);
        }
        if (parentId != null) {
            folderRepo.findByIdAndGroupId(parentId, groupId)
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        }
        boolean duplicate = parentId == null
                ? folderRepo.existsByGroupIdAndParentIdIsNullAndName(groupId, name)
                : folderRepo.existsByGroupIdAndParentIdAndName(groupId, parentId, name);
        if (duplicate) {
            throw new AppException(ErrorCode.FOLDER_NAME_TAKEN);
        }
        GroupFolder saved = folderRepo.save(GroupFolder.builder()
                .groupId(groupId)
                .parentId(parentId)
                .name(name)
                .createdById(requesterId)
                .build());
        return GroupFolderResponse.from(saved);
    }

    @Transactional
    public void delete(UUID groupId, UUID folderId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        GroupFolder folder = folderRepo.findByIdAndGroupId(folderId, groupId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        if (!folder.getCreatedById().equals(requesterId)
                && !groupInternalService.isOwner(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_FOLDER_CREATOR_OR_GROUP_OWNER);
        }
        if (folderRepo.existsByParentId(folderId) || fileRepo.existsByFolderId(folderId)) {
            throw new AppException(ErrorCode.FOLDER_NOT_EMPTY);
        }
        folderRepo.delete(folder);
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
