package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.WebSocketUserTracker;
import com.ronkadosh.bubbleup.groups.api.dto.PresenceResponse;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.model.UserPresence;
import com.ronkadosh.bubbleup.groups.persistence.UserPresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read side of presence. Backs {@code GET /api/groups/{id}/presence}. Writes (connect /
 * disconnect listeners) live on {@link PresenceCommandService}.
 */
@Service
@RequiredArgsConstructor
public class PresenceQueryService {

    private final UserPresenceRepository userPresenceRepository;
    private final GroupInternalService groupInternalService;
    private final WebSocketUserTracker webSocketUserTracker;

    @Transactional(readOnly = true)
    public List<PresenceResponse> snapshotForGroup(UUID groupId, UUID requesterId) {
        if (!groupInternalService.groupExists(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isMember(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        List<UUID> memberIds = groupInternalService.getMemberUserIds(groupId);
        if (memberIds.isEmpty()) return List.of();
        Map<UUID, Instant> lastSeen = lastSeenByUserId(memberIds);
        return memberIds.stream()
                .map(uid -> new PresenceResponse(
                        uid,
                        webSocketUserTracker.isUserConnected(uid),
                        lastSeen.get(uid)
                ))
                .toList();
    }

    private Map<UUID, Instant> lastSeenByUserId(Collection<UUID> userIds) {
        Map<UUID, Instant> out = new HashMap<>();
        for (UserPresence p : userPresenceRepository.findAllByUserIdIn(userIds)) {
            out.put(p.getUserId(), p.getLastSeenAt());
        }
        return out;
    }
}
