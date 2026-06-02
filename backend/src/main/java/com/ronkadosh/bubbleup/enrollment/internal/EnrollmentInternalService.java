package com.ronkadosh.bubbleup.enrollment.internal;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module read surface for enrollments. Used by the groups module to gate
 * the per-course study group listing on the caller having actually enrolled in
 * the course for the current term.
 */
public interface EnrollmentInternalService {

    /**
     * True iff {@code userId} has an enrollment row whose offering matches
     * {@code courseId} for the user's university's current term. Returns false
     * (rather than throwing) when the user has no affiliation, no current term,
     * or no offering — the gate degrades to "no access" cleanly.
     */
    boolean isEnrolledInCourseCurrentTerm(UUID userId, UUID courseId);

    /**
     * The list of courseIds the user is enrolled in for the current term.
     * Empty when no affiliation / no current term / no enrollments.
     */
    List<UUID> enrolledCourseIdsForCurrentTerm(UUID userId);

    /**
     * True iff the user has an enrollment row for this exact offering (course+term).
     * This is the precise gate for group membership: a bubble carries an
     * {@code offeringId}, and the offering already encodes both course and term.
     */
    boolean isEnrolledInOffering(UUID userId, UUID offeringId);
}
