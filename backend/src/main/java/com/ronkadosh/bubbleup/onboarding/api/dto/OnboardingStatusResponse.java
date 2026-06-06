package com.ronkadosh.bubbleup.onboarding.api.dto;

import com.ronkadosh.bubbleup.matching.internal.dto.MatchingReliability;

import java.util.Set;

/**
 * Live onboarding progress for the dashboard "Getting started" widget. Action-step
 * fields are derived from other modules' read surfaces; {@code acknowledged} +
 * {@code collapsed} are the user's persisted UI state (so it's consistent across
 * devices and reloads).
 *
 * <p>The frontend widget renders only while {@code complete} is false, so a fully
 * onboarded ("veteran") user never sees it regardless of acknowledgements.
 *
 * @param complete     all action steps done (study base + in a bubble) → hide the widget
 * @param studyBase    affiliation + course-enrollment progress
 * @param inBubble     member of at least one bubble
 * @param reliability  the user's private matching profile strength (also shown in Settings)
 * @param acknowledged namespaced keys the user has dismissed (e.g. {@code explainer:whatIsBubble}, {@code guide:enroll})
 * @param collapsed    whether the user has collapsed the widget
 * @param wizardLevel  current onboarding-wizard level (1–5; 6 = finished)
 */
public record OnboardingStatusResponse(
        boolean complete,
        StudyBaseStep studyBase,
        boolean inBubble,
        MatchingReliability reliability,
        Set<String> acknowledged,
        boolean collapsed,
        int wizardLevel
) {
    /** "Set up your study base": pick a university + department, then enroll in courses. */
    public record StudyBaseStep(boolean affiliationDone, boolean coursesDone) {
        public boolean done() {
            return affiliationDone && coursesDone;
        }
    }
}
