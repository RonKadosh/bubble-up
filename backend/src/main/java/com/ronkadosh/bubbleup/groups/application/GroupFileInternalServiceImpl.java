package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.groups.internal.GroupFileInternalService;
import com.ronkadosh.bubbleup.groups.model.GroupFile;
import com.ronkadosh.bubbleup.groups.persistence.GroupFileRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupFileInternalServiceImpl implements GroupFileInternalService {

    private final GroupFileRepository repo;
    private final GroupFolderRepository folderRepo;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public void deleteFilesForGroup(UUID groupId) {
        List<GroupFile> rows = repo.findAllByGroupId(groupId);
        if (rows.isEmpty()) return;
        for (GroupFile row : rows) {
            try {
                fileStorageService.delete(row.getFileId());
            } catch (RuntimeException ignored) {
                // best-effort: storage may already be gone; DB row deletion proceeds
            }
        }
        repo.deleteAllByGroupId(groupId);
    }

    @Override
    @Transactional
    public void deleteFoldersForGroup(UUID groupId) {
        folderRepo.deleteAllByGroupId(groupId);
    }
}
