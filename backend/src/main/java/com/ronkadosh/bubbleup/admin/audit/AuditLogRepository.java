package com.ronkadosh.bubbleup.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    @Query("""
            select a from AuditLogEntry a
            where (:action = '' or a.action = :action)
              and (:targetType = '' or a.targetType = :targetType)
              and (:actorId is null or a.actorId = :actorId)
              and a.createdAt >= :createdAfter
            """)
    Page<AuditLogEntry> search(
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("actorId") UUID actorId,
            @Param("createdAfter") Instant createdAfter,
            Pageable pageable
    );
}
