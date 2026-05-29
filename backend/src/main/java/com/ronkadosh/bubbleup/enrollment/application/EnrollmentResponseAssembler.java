package com.ronkadosh.bubbleup.enrollment.application;

import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.enrollment.api.dto.EnrollmentResponse;
import com.ronkadosh.bubbleup.enrollment.model.Enrollment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hydrates {@link Enrollment} rows into {@link EnrollmentResponse}s by resolving
 * the offering → course/term refs through {@link CatalogInternalService}. Exposed
 * as a {@code @Component} so both the command and query services share the
 * same mapping path.
 *
 * <p>{@link #toResponses(Collection)} batches the catalog lookups — the list path
 * stays O(1) catalog queries regardless of how many enrollments the user has.
 */
@Component
@RequiredArgsConstructor
public class EnrollmentResponseAssembler {

    private final CatalogInternalService catalogInternalService;

    public EnrollmentResponse toResponse(Enrollment e) {
        OfferingRef offering = catalogInternalService.getOfferingRef(e.getOfferingId()).orElse(null);
        if (offering == null) {
            return new EnrollmentResponse(
                    e.getId(), e.getUserId(), e.getOfferingId(),
                    null, null, null, null, null, e.getCreatedAt());
        }
        return new EnrollmentResponse(
                e.getId(),
                e.getUserId(),
                e.getOfferingId(),
                offering.courseId(),
                offering.courseCode(),
                offering.courseName(),
                offering.termId(),
                offering.termCode(),
                e.getCreatedAt()
        );
    }

    public List<EnrollmentResponse> toResponses(Collection<Enrollment> rows) {
        if (rows.isEmpty()) return List.of();
        Set<UUID> offeringIds = new HashSet<>();
        for (Enrollment e : rows) offeringIds.add(e.getOfferingId());
        Map<UUID, OfferingRef> offerings = new HashMap<>();
        for (UUID id : offeringIds) {
            catalogInternalService.getOfferingRef(id).ifPresent(ref -> offerings.put(id, ref));
        }
        return rows.stream()
                .map(e -> {
                    OfferingRef o = offerings.get(e.getOfferingId());
                    if (o == null) {
                        return new EnrollmentResponse(
                                e.getId(), e.getUserId(), e.getOfferingId(),
                                null, null, null, null, null, e.getCreatedAt());
                    }
                    return new EnrollmentResponse(
                            e.getId(), e.getUserId(), e.getOfferingId(),
                            o.courseId(), o.courseCode(), o.courseName(),
                            o.termId(), o.termCode(), e.getCreatedAt());
                })
                .toList();
    }
}
