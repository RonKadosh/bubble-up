package com.ronkadosh.bubbleup.groups.persistence;

import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<StudyGroup, UUID> {

    List<StudyGroup> findAllByOfferingId(UUID offeringId);
    List<StudyGroup> findAllByOfferingIdAndStatus(UUID offeringId, GroupStatus status);

    List<StudyGroup> findAllByOfferingIdIn(Collection<UUID> offeringIds);
    List<StudyGroup> findAllByOfferingIdInAndStatus(Collection<UUID> offeringIds, GroupStatus status);

    List<StudyGroup> findByOfferingIdInAndVisibilityAndStatusAndIdNotIn(
            Collection<UUID> offeringIds, GroupVisibility visibility, GroupStatus status, Collection<UUID> excludedIds, Pageable pageable);

    List<StudyGroup> findByOfferingIdInAndVisibilityAndStatus(
            Collection<UUID> offeringIds, GroupVisibility visibility, GroupStatus status, Pageable pageable);

    @Query("SELECT g FROM StudyGroup g WHERE g.visibility = :visibility AND g.status = :status AND g.id NOT IN :excluded " +
           "ORDER BY (SELECT COUNT(m) FROM GroupMember m WHERE m.groupId = g.id) DESC")
    List<StudyGroup> findTopPublicExcluding(
            @Param("visibility") GroupVisibility visibility,
            @Param("status") GroupStatus status,
            @Param("excluded") Collection<UUID> excluded,
            Pageable pageable);

    @Query("SELECT g FROM StudyGroup g WHERE g.visibility = :visibility AND g.status = :status " +
           "ORDER BY (SELECT COUNT(m) FROM GroupMember m WHERE m.groupId = g.id) DESC")
    List<StudyGroup> findTopPublic(
            @Param("visibility") GroupVisibility visibility,
            @Param("status") GroupStatus status,
            Pageable pageable);

    long countByOfferingId(UUID offeringId);
    long countByOfferingIdIn(Collection<UUID> offeringIds);
    long countByStatus(GroupStatus status);
    long countByOfferingIdInAndStatus(Collection<UUID> offeringIds, GroupStatus status);
    long countByCreatedAtAfter(java.time.Instant since);
    List<StudyGroup> findTop50ByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("""
            select g from StudyGroup g
            where (:q = '' or lower(g.name) like concat('%', :q, '%'))
            """)
    org.springframework.data.domain.Page<StudyGroup> searchForAdmin(
            @org.springframework.data.repository.query.Param("q") String q,
            Pageable pageable
    );
}
