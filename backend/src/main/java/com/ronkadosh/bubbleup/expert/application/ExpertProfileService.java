package com.ronkadosh.bubbleup.expert.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.common.config.ExpertProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.api.dto.ApplyAsExpertRequest;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertProfileResponse;
import com.ronkadosh.bubbleup.expert.api.dto.UpdateExpertProfileRequest;
import com.ronkadosh.bubbleup.expert.model.ExpertProfile;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import com.ronkadosh.bubbleup.expert.persistence.ExpertProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpertProfileService {

    private final ExpertProfileRepository repo;
    private final AuthInternalService authInternalService;
    private final TimeProvider timeProvider;
    private final ExpertProperties expertProperties;

    @Transactional
    public ExpertProfileResponse applyAsExpert(UUID userId, ApplyAsExpertRequest request) {
        if (!authInternalService.userExists(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (repo.existsByUserId(userId)) {
            throw new AppException(ErrorCode.EXPERT_APPLICATION_ALREADY_SUBMITTED);
        }
        ExpertProfile.ExpertProfileBuilder builder = ExpertProfile.builder()
                .userId(userId)
                .headline(request.headline())
                .bio(request.bio())
                .expertiseTags(request.expertiseTags() == null
                        ? new HashSet<>()
                        : new HashSet<>(request.expertiseTags()));
        if (expertProperties.autoVerify()) {
            builder.verificationStatus(VerificationStatus.VERIFIED)
                    .verifiedAt(timeProvider.now())
                    .verifiedBy(null);
        } else {
            builder.verificationStatus(VerificationStatus.PENDING)
                    .verifiedAt(null)
                    .verifiedBy(null);
        }
        ExpertProfile saved = repo.save(builder.build());
        // Role is granted on verification, not on apply: an auto-verified
        // applicant becomes EXPERT immediately; a PENDING applicant stays a
        // STUDENT until an admin approves (AdminExpertVerificationService.verify).
        if (saved.getVerificationStatus() == VerificationStatus.VERIFIED) {
            authInternalService.promoteToExpert(userId);
        }
        return ExpertProfileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ExpertProfileResponse getMyProfile(UUID userId) {
        ExpertProfile profile = repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        return ExpertProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public ExpertProfileResponse getPublicProfile(UUID userId) {
        ExpertProfile profile = repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        return ExpertProfileResponse.from(profile);
    }

    @Transactional
    public ExpertProfileResponse updateMyProfile(UUID userId, UpdateExpertProfileRequest request) {
        ExpertProfile profile = repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        if (request.headline() != null) profile.setHeadline(request.headline());
        if (request.bio() != null) profile.setBio(request.bio());
        if (request.expertiseTags() != null) profile.replaceTags(new HashSet<>(request.expertiseTags()));
        return ExpertProfileResponse.from(repo.save(profile));
    }

    @Transactional(readOnly = true)
    public List<ExpertProfileResponse> listVerifiedDirectory() {
        return repo.findByVerificationStatus(VerificationStatus.VERIFIED).stream()
                .map(ExpertProfileResponse::from)
                .toList();
    }
}
