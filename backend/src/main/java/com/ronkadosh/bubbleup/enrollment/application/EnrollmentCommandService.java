package com.ronkadosh.bubbleup.enrollment.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.CourseRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.enrollment.api.dto.EnrollmentResponse;
import com.ronkadosh.bubbleup.enrollment.model.Enrollment;
import com.ronkadosh.bubbleup.enrollment.persistence.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrollmentCommandService {

    private final EnrollmentRepository repo;
    private final AuthInternalService authInternalService;
    private final CatalogInternalService catalogInternalService;
    private final EnrollmentResponseAssembler assembler;

    @Transactional
    public EnrollmentResponse enroll(UUID userId, UUID courseId) {
        UserProfile profile = authInternalService.getProfile(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (profile.universityId() == null) {
            throw new AppException(ErrorCode.USER_AFFILIATION_REQUIRED);
        }
        CourseRef course = catalogInternalService.getCourseRef(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.universityId().equals(profile.universityId())) {
            // Treat as "not found" rather than leaking which uni owns the course.
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }
        TermRef term = catalogInternalService.currentTermFor(profile.universityId())
                .orElseThrow(() -> new AppException(ErrorCode.CURRENT_TERM_NOT_FOUND));
        UUID offeringId = catalogInternalService.offeringIdForCourseAndTerm(courseId, term.id())
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NO_CURRENT_OFFERING));
        if (repo.existsByUserIdAndOfferingId(userId, offeringId)) {
            throw new AppException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
        }
        Enrollment saved = repo.save(Enrollment.builder()
                .userId(userId)
                .offeringId(offeringId)
                .build());
        return assembler.toResponse(saved);
    }

    @Transactional
    public void unenroll(UUID userId, UUID enrollmentId) {
        Enrollment row = repo.findById(enrollmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!row.getUserId().equals(userId)) {
            // Don't leak existence to non-owners.
            throw new AppException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        repo.deleteById(enrollmentId);
    }
}
