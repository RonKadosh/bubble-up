package com.ronkadosh.studybuddy.auth.application;

import com.ronkadosh.studybuddy.auth.model.RefreshToken;
import com.ronkadosh.studybuddy.auth.persistence.RefreshTokenRepository;
import com.ronkadosh.studybuddy.common.datetime.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lives in its own bean so the chain revocation commits in an independent transaction
 * (REQUIRES_NEW). Otherwise a caller that throws right after invoking the revoker would
 * roll back the revocation along with its own transaction — defeating reuse-detection.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenChainRevoker {

    private final RefreshTokenRepository repo;
    private final TimeProvider timeProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeChain(UUID anyTokenIdInChain) {
        Set<UUID> toRevoke = new HashSet<>();
        UUID cursor = anyTokenIdInChain;
        while (cursor != null && toRevoke.add(cursor)) {
            Optional<RefreshToken> ancestor = repo.findById(cursor);
            if (ancestor.isEmpty()) break;
            cursor = ancestor.get().getRotatedFromId();
        }
        Deque<UUID> queue = new ArrayDeque<>(toRevoke);
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            List<RefreshToken> children = repo.findAllByRotatedFromId(id);
            for (RefreshToken c : children) {
                if (toRevoke.add(c.getId())) queue.add(c.getId());
            }
        }
        if (!toRevoke.isEmpty()) {
            repo.revokeAll(new ArrayList<>(toRevoke), timeProvider.now());
        }
    }
}
