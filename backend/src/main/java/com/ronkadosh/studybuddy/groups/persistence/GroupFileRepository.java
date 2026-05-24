package com.ronkadosh.studybuddy.groups.persistence;

import com.ronkadosh.studybuddy.groups.model.GroupFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupFileRepository extends JpaRepository<GroupFile, UUID> {

    List<GroupFile> findAllByGroupId(UUID groupId);

    Optional<GroupFile> findByIdAndGroupId(UUID id, UUID groupId);

    @Modifying
    @Query("delete from GroupFile f where f.groupId = :groupId")
    int deleteAllByGroupId(@Param("groupId") UUID groupId);
}
