package com.ronkadosh.bubbleup.help.persistence;

import com.ronkadosh.bubbleup.help.model.HelpQuestionEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HelpQuestionEntryRepository extends JpaRepository<HelpQuestionEntry, UUID> {

    Optional<HelpQuestionEntry> findFirstByNormalizedQuestionAndLocaleAndSourceAndCacheableTrueOrderByCreatedAtDesc(
            String normalizedQuestion,
            String locale,
            String source
    );

    Page<HelpQuestionEntry> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            select h from HelpQuestionEntry h
            where h.userId = :userId
              and (:query = '' or h.normalizedQuestion like concat('%', :query, '%'))
            order by h.createdAt desc
            """)
    Page<HelpQuestionEntry> searchUserEntries(
            @Param("userId") UUID userId,
            @Param("query") String query,
            Pageable pageable
    );
}
