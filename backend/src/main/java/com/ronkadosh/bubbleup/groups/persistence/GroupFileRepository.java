package com.ronkadosh.bubbleup.groups.persistence;

import com.ronkadosh.bubbleup.groups.model.GroupFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupFileRepository extends JpaRepository<GroupFile, UUID> {

    List<GroupFile> findAllByGroupId(UUID groupId);

    List<GroupFile> findAllByGroupIdAndFolderId(UUID groupId, UUID folderId);

    List<GroupFile> findAllByGroupIdAndFolderIdIsNull(UUID groupId);

    Optional<GroupFile> findByIdAndGroupId(UUID id, UUID groupId);

    boolean existsByFolderId(UUID folderId);

    @Modifying
    @Query("delete from GroupFile f where f.groupId = :groupId")
    int deleteAllByGroupId(@Param("groupId") UUID groupId);
}
