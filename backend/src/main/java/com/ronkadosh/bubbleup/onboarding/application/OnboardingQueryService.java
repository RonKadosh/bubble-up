package com.ronkadosh.bubbleup.onboarding.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.enrollment.internal.EnrollmentInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.matching.internal.MatchingInternalService;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchingReliability;
import com.ronkadosh.bubbleup.onboarding.api.dto.OnboardingStatusResponse;
import com.ronkadosh.bubbleup.onboarding.api.dto.OnboardingStatusResponse.StudyBaseStep;
import com.ronkadosh.bubbleup.onboarding.model.OnboardingState;
import com.ronkadosh.bubbleup.onboarding.persistence.OnboardingStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Assembles onboarding progress purely from other modules' {@code internal/}
 * surfaces — this module owns no entities or tables. Each step's completion is
 * derived live, so it can never drift out of sync with the source of truth.
 */
@Service
@RequiredArgsConstructor
public class OnboardingQueryService {

    private final AuthInternalService authInternalService;
    private final EnrollmentInternalService enrollmentInternalService;
    private final GroupInternalService groupInternalService;
    private final MatchingInternalService matchingInternalService;
    private final OnboardingStateRepository stateRepository;

    @Transactional(readOnly = true)
    public OnboardingStatusResponse getStatus(UUID userId) {
        boolean affiliationDone = authInternalService.getProfile(userId)
                .map(UserProfile::hasAffiliation)
                .orElse(false);
        boolean coursesDone = !enrollmentInternalService
                .enrolledCourseIdsForCurrentTerm(userId).isEmpty();
        boolean inBubble = !groupInternalService.getGroupsForUser(userId).isEmpty();

        StudyBaseStep studyBase = new StudyBaseStep(affiliationDone, coursesDone);
        boolean complete = studyBase.done() && inBubble;

        MatchingReliability reliability = matchingInternalService.getReliability(userId);

        // Persisted UI state. A plain read never creates a row — absent → defaults.
        OnboardingState state = stateRepository.findByUserId(userId).orElse(null);
        Set<String> acknowledged = state != null ? Set.copyOf(state.getAcknowledged()) : Set.of();
        boolean collapsed = state != null && state.isCollapsed();
        int wizardLevel = state != null ? state.getWizardLevel() : 1;

        return new OnboardingStatusResponse(
                complete, studyBase, inBubble, reliability, acknowledged, collapsed, wizardLevel);
    }
}
