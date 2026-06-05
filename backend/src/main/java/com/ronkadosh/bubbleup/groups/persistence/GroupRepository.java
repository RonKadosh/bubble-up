package com.ronkadosh.bubbleup.groups.persistence;

import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<StudyGroup, UUID> {

    List<StudyGroup> findAllByOfferingId(UUID offeringId);

    List<StudyGroup> findAllByOfferingIdIn(Collection<UUID> offeringIds);

    List<StudyGroup> findByOfferingIdInAndVisibilityAndIdNotIn(
            Collection<UUID> offeringIds, GroupVisibility visibility, Collection<UUID> excludedIds, Pageable pageable);

    List<StudyGroup> findByOfferingIdInAndVisibility(
            Collection<UUID> offeringIds, GroupVisibility visibility, Pageable pageable);

    long countByOfferingId(UUID offeringId);
    long countByOfferingIdIn(Collection<UUID> offeringIds);
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
