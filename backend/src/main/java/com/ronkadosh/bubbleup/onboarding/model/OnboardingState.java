package com.ronkadosh.bubbleup.onboarding.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user onboarding UI state — the only thing the onboarding module persists.
 * Step completion is still derived live from other modules (see
 * {@code OnboardingQueryService}); this stores just the user's own acknowledgements
 * so the "Getting started" experience is consistent across devices and reloads.
 *
 * <p>{@code acknowledged} is an open, namespaced key set (e.g. {@code explainer:whatIsBubble},
 * {@code guide:enroll}). New explainers/guides reuse it with a new key — no schema change.
 */
@Entity
@Table(
        name = "onboarding_state",
        indexes = @Index(name = "idx_onboarding_state_user", columnList = "user_id", unique = true)
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "onboarding_acknowledged",
            joinColumns = @JoinColumn(name = "onboarding_state_id"),
            indexes = @Index(name = "idx_onboarding_ack_state", columnList = "onboarding_state_id")
    )
    @Column(name = "ack_key", length = 64, nullable = false)
    @Builder.Default
    private Set<String> acknowledged = new HashSet<>();

    @Column(name = "collapsed", nullable = false)
    @Setter
    @Builder.Default
    private boolean collapsed = false;

    /** Current level of the onboarding wizard (1–5; 6 = finished). Persisted so progress resumes anywhere. */
    @Column(name = "wizard_level", nullable = false)
    @Setter
    @Builder.Default
    private int wizardLevel = 1;

    @Column(name = "updated_at", nullable = false)
    @Setter
    private Instant updatedAt;

    public void acknowledge(String key) {
        acknowledged.add(key);
    }
}
