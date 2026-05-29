package com.ronkadosh.bubbleup.enrollment.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.OfferingRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.enrollment.api.dto.EnrollmentResponse;
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
public class EnrollmentQueryService {

    private final EnrollmentRepository repo;
    private final AuthInternalService authInternalService;
    private final CatalogInternalService catalogInternalService;
    private final EnrollmentResponseAssembler assembler;

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listMine(UUID userId) {
        return assembler.toResponses(repo.findAllByUserId(userId));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listMineForCurrentTerm(UUID userId) {
        Optional<UUID> termId = authInternalService.getProfile(userId)
                .map(UserProfile::universityId)
                .flatMap(uniId -> uniId == null
                        ? Optional.empty()
                        : catalogInternalService.currentTermFor(uniId).map(TermRef::id));
        if (termId.isEmpty()) {
            return List.of();
        }
        List<Enrollment> all = repo.findAllByUserId(userId);
        // Filter to offerings whose term matches the current term.
        List<Enrollment> currentTerm = all.stream()
                .filter(e -> catalogInternalService.getOfferingRef(e.getOfferingId())
                        .map(OfferingRef::termId)
                        .map(t -> t.equals(termId.get()))
                        .orElse(false))
                .toList();
        return assembler.toResponses(currentTerm);
    }
}
