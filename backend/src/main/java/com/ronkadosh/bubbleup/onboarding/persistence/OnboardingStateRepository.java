package com.ronkadosh.bubbleup.onboarding.persistence;

import com.ronkadosh.bubbleup.onboarding.model.OnboardingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingStateRepository extends JpaRepository<OnboardingState, UUID> {
    Optional<OnboardingState> findByUserId(UUID userId);
}
