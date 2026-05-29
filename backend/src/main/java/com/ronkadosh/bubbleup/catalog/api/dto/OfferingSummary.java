package com.ronkadosh.bubbleup.catalog.api.dto;

import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;

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
}
