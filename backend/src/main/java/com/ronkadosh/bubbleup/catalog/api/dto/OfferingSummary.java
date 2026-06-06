package com.ronkadosh.bubbleup.catalog.api.dto;

import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Term;

import java.util.UUID;

public record OfferingSummary(
        UUID id,
        UUID courseId,
        UUID termId,
        UUID universityId,
        String courseCode,
        String courseName,
        String termCode
) {
    public static OfferingSummary from(OfferingRef o) {
        return new OfferingSummary(
                o.id(), o.courseId(), o.termId(), o.universityId(),
                o.courseCode(), o.courseName(), o.termCode());
    }

    /** Build directly from already-loaded entities, avoiding a per-offering re-fetch. */
    public static OfferingSummary from(CourseOffering offering, Course course, Term term) {
        return new OfferingSummary(
                offering.getId(), course.getId(), term.getId(), course.getUniversityId(),
                course.getCode(), course.getName(), term.getCode());
    }
}
