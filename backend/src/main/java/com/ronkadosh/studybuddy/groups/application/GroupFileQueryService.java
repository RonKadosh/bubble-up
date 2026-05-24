package com.ronkadosh.studybuddy.groups.application;

import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.file.FileStorageService;
import com.ronkadosh.studybuddy.groups.api.dto.GroupFileResponse;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import com.ronkadosh.studybuddy.groups.model.GroupFile;
import com.ronkadosh.studybuddy.groups.persistence.GroupFileRepository;
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

    @Transactional(readOnly = true)
    public List<GroupFileResponse> list(UUID groupId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        return repo.findAllByGroupId(groupId).stream()
                .map(GroupFileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(UUID groupId, UUID groupFileId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        GroupFile row = repo.findByIdAndGroupId(groupFileId, groupId)
                .orElseThrow(() -> new AppException(ErrorCode.GROUP_FILE_NOT_FOUND));
        byte[] bytes = fileStorageService.download(row.getFileId());
        return new DownloadedFile(bytes, row.getContentType(), row.getOriginalName(), row.getSizeBytes());
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
