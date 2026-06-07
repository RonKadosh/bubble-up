package com.ronkadosh.bubbleup.admin.audit;

import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.pagination.PageMapper;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuditLogRepository repo;
    private final CurrentUserProvider currentUserProvider;
    private final PageMapper pageMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, UUID targetId, String reason, String metadata) {
        CurrentUser actor = currentUserProvider.isAuthenticated() ? currentUserProvider.get() : null;
        repo.save(AuditLogEntry.builder()
                .actorId(actor != null ? actor.id() : null)
                .actorEmail(actor != null ? actor.email() : null)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .metadata(metadata)
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponseDto<AuditLogResponse> search(
            String action, String targetType, UUID actorId, Instant createdAfter,
            int page, int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = repo.search(
                        action == null ? "" : action,
                        targetType == null ? "" : targetType,
                        actorId,
                        createdAfter == null ? Instant.EPOCH : createdAfter,
                        pageable)
                .map(AuditLogResponse::from);
        return pageMapper.toDto(result);
    }

    public record AuditLogResponse(
            UUID id,
            UUID actorId,
            String actorEmail,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            String metadata,
            Instant createdAt
    ) {
        static AuditLogResponse from(AuditLogEntry entry) {
            return new AuditLogResponse(
                    entry.getId(),
                    entry.getActorId(),
                    entry.getActorEmail(),
                    entry.getAction(),
                    entry.getTargetType(),
                    entry.getTargetId(),
                    entry.getReason(),
                    entry.getMetadata(),
                    entry.getCreatedAt()
            );
        }
    }
}
