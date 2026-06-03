package com.ronkadosh.bubbleup.onboarding.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.onboarding.model.OnboardingState;
import com.ronkadosh.bubbleup.onboarding.persistence.OnboardingStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes to the user's onboarding UI state. The row is created lazily on the first
 * acknowledgement / collapse — a user who never interacts never gets a row, and a
 * plain status read never creates one.
 */
@Service
@RequiredArgsConstructor
public class OnboardingCommandService {

    private final OnboardingStateRepository repo;
    private final TimeProvider timeProvider;

    @Transactional
    public void acknowledge(UUID userId, String key) {
        OnboardingState state = getOrCreate(userId);
        state.acknowledge(key);
        state.setUpdatedAt(timeProvider.now());
        repo.save(state);
    }

    @Transactional
    public void setCollapsed(UUID userId, boolean collapsed) {
        OnboardingState state = getOrCreate(userId);
        state.setCollapsed(collapsed);
        state.setUpdatedAt(timeProvider.now());
        repo.save(state);
    }

    @Transactional
    public void setWizardLevel(UUID userId, int level) {
        OnboardingState state = getOrCreate(userId);
        state.setWizardLevel(level);
        state.setUpdatedAt(timeProvider.now());
        repo.save(state);
    }

    private OnboardingState getOrCreate(UUID userId) {
        return repo.findByUserId(userId).orElseGet(() -> OnboardingState.builder()
                .userId(userId)
                .updatedAt(timeProvider.now())
                .build());
    }
}
