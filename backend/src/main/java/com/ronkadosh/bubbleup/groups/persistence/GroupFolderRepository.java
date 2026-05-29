package com.ronkadosh.bubbleup.groups.persistence;

import com.ronkadosh.bubbleup.groups.model.GroupFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupFolderRepository extends JpaRepository<GroupFolder, UUID> {

    List<GroupFolder> findAllByGroupId(UUID groupId);

    Optional<GroupFolder> findByIdAndGroupId(UUID id, UUID groupId);

    boolean existsByGroupIdAndParentIdAndName(UUID groupId, UUID parentId, String name);

    boolean existsByGroupIdAndParentIdIsNullAndName(UUID groupId, String name);

    boolean existsByParentId(UUID parentId);

    @Modifying
    @Query("delete from GroupFolder f where f.groupId = :groupId")
    int deleteAllByGroupId(@Param("groupId") UUID groupId);
}
