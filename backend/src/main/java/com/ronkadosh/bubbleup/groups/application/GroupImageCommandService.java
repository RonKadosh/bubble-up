package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileAccessPolicy;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.common.file.FileUploadRequest;
import com.ronkadosh.bubbleup.common.file.StoredFile;
import com.ronkadosh.bubbleup.groups.api.dto.GroupResponse;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Bubble cover-image command side — the group-scope twin of the avatar replace/delete
 * in {@code UserProfileCommandService}. Owner-only. The upload happens outside the DB
 * transaction so a failure leaves no orphan row; the column swap is its own tight
 * transaction; the old file is best-effort-deleted only after the swap commits.
 */
@Service
@Slf4j
public class GroupImageCommandService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final FileStorageService fileStorageService;
    private final GroupImageTypeFilter groupImageTypeFilter;
    private final TransactionTemplate transactionTemplate;

    public GroupImageCommandService(
            GroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            FileStorageService fileStorageService,
            GroupImageTypeFilter groupImageTypeFilter,
            PlatformTransactionManager transactionManager
    ) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.fileStorageService = fileStorageService;
        this.groupImageTypeFilter = groupImageTypeFilter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public GroupResponse replaceImage(UUID groupId, UUID requesterId, byte[] bytes, String contentType, String originalName) {
        requireActiveOwner(groupId, requesterId);
        groupImageTypeFilter.requireAllowed(contentType, bytes == null ? 0L : bytes.length);
        StoredFile uploaded = fileStorageService.upload(new FileUploadRequest(
                originalName == null ? "bubble-image" : originalName,
                contentType,
                bytes,
                FileAccessPolicy.PUBLIC));
        SwapResult result;
        try {
            result = transactionTemplate.execute(status -> swapImage(groupId, uploaded.fileId(), contentType));
        } catch (RuntimeException ex) {
            bestEffortDelete(uploaded.fileId());
            throw ex;
        }
        if (result != null && result.oldFileId() != null) bestEffortDelete(result.oldFileId());
        return toResponse(result == null ? loadGroup(groupId) : result.group());
    }

    public GroupResponse deleteImage(UUID groupId, UUID requesterId) {
        requireActiveOwner(groupId, requesterId);
        SwapResult result = transactionTemplate.execute(status -> swapImage(groupId, null, null));
        if (result != null && result.oldFileId() != null) bestEffortDelete(result.oldFileId());
        return toResponse(result == null ? loadGroup(groupId) : result.group());
    }

    private SwapResult swapImage(UUID groupId, String newFileId, String newContentType) {
        StudyGroup group = loadGroup(groupId);
        String oldFileId = group.getImageFileId();
        group.setImageFileId(newFileId);
        group.setImageContentType(newFileId == null ? null : newContentType);
        groupRepository.save(group);
        return new SwapResult(group, oldFileId);
    }

    private record SwapResult(StudyGroup group, String oldFileId) {}

    /** Owner-gate + active-gate, mirroring {@code GroupCommandService.updateGroup}. */
    private void requireActiveOwner(UUID groupId, UUID requesterId) {
        StudyGroup group = loadGroup(groupId);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new AppException(ErrorCode.GROUP_NOT_ACTIVE);
        }
        MembershipRole role = memberRepository.findByGroupIdAndUserId(groupId, requesterId)
                .map(GroupMember::getRole)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_GROUP_OWNER));
        if (role != MembershipRole.OWNER) {
            throw new AppException(ErrorCode.NOT_GROUP_OWNER);
        }
    }

    private GroupResponse toResponse(StudyGroup group) {
        UUID ownerId = memberRepository.findAllByGroupIdAndRole(group.getId(), MembershipRole.OWNER).stream()
                .findFirst()
                .map(GroupMember::getUserId)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_ERROR));
        long count = memberRepository.countByGroupId(group.getId());
        return GroupResponse.from(group, ownerId, count);
    }

    private void bestEffortDelete(String fileId) {
        try {
            fileStorageService.delete(fileId);
        } catch (Exception e) {
            log.warn("Best-effort delete of group image file {} failed: {}", fileId, e.getMessage());
        }
    }

    private StudyGroup loadGroup(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_NOT_FOUND));
    }
}
