package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.groups.api.dto.GroupFolderResponse;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.persistence.GroupFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupFolderQueryService {

    private final GroupFolderRepository repo;
    private final GroupInternalService groupInternalService;

    @Transactional(readOnly = true)
    public List<GroupFolderResponse> list(UUID groupId, UUID requesterId) {
        requireGroupAndMembership(groupId, requesterId);
        return repo.findAllByGroupId(groupId).stream()
                .map(GroupFolderResponse::from)
                .toList();
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
