package com.ronkadosh.bubbleup.enrollment.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.enrollment.internal.EnrollmentInternalService;
import com.ronkadosh.bubbleup.enrollment.model.Enrollment;
import com.ronkadosh.bubbleup.enrollment.persistence.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrollmentInternalServiceImpl implements EnrollmentInternalService {

    private final EnrollmentRepository repo;
    private final AuthInternalService authInternalService;
    private final CatalogInternalService catalogInternalService;

    @Override
    @Transactional(readOnly = true)
    public boolean isEnrolledInCourseCurrentTerm(UUID userId, UUID courseId) {
        return currentTermOfferingForCourse(userId, courseId)
                .map(offeringId -> repo.existsByUserIdAndOfferingId(userId, offeringId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> enrolledCourseIdsForCurrentTerm(UUID userId) {
        Optional<UUID> termId = currentTermIdForUser(userId);
        if (termId.isEmpty()) return List.of();
        List<Enrollment> rows = repo.findAllByUserId(userId);
        return rows.stream()
                .map(e -> catalogInternalService.getOfferingRef(e.getOfferingId()).orElse(null))
                .filter(o -> o != null && o.termId().equals(termId.get()))
                .map(OfferingRef::courseId)
                .toList();
    }

    private Optional<UUID> currentTermOfferingForCourse(UUID userId, UUID courseId) {
        return currentTermIdForUser(userId)
                .flatMap(termId -> catalogInternalService.offeringIdForCourseAndTerm(courseId, termId));
    }

    private Optional<UUID> currentTermIdForUser(UUID userId) {
        UUID uniId = authInternalService.getProfile(userId)
                .map(UserProfile::universityId)
                .orElse(null);
        if (uniId == null) return Optional.empty();
        return catalogInternalService.currentTermFor(uniId).map(TermRef::id);
    }
}
