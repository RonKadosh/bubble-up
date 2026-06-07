package com.ronkadosh.bubbleup.expert.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.internal.ExpertAdminInternalService;
import com.ronkadosh.bubbleup.expert.internal.dto.admin.ExpertAdminProfileDto;
import com.ronkadosh.bubbleup.expert.model.ExpertProfile;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import com.ronkadosh.bubbleup.expert.persistence.ExpertProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpertAdminInternalServiceImpl implements ExpertAdminInternalService {

    private final ExpertProfileRepository repo;
    private final TimeProvider timeProvider;

    @Override
    @Transactional(readOnly = true)
    public List<ExpertAdminProfileDto> listByStatus(VerificationStatus status) {
        return repo.findByVerificationStatus(status).stream()
                .map(ExpertAdminInternalServiceImpl::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExpertAdminProfileDto getByUser(UUID userId) {
        return repo.findByUserId(userId)
                .map(ExpertAdminInternalServiceImpl::toDto)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
    }

    @Override
    @Transactional
    public ExpertAdminProfileDto verify(UUID userId, UUID adminUserId) {
        ExpertProfile p = repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        if (p.getVerificationStatus() == VerificationStatus.VERIFIED) {
            return toDto(p);
        }
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        p.setVerifiedAt(timeProvider.now());
        p.setVerifiedBy(adminUserId);
        return toDto(repo.save(p));
    }

    @Override
    @Transactional
    public ExpertAdminProfileDto revoke(UUID userId) {
        ExpertProfile p = repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        p.setVerificationStatus(VerificationStatus.PENDING);
        p.setVerifiedAt(null);
        p.setVerifiedBy(null);
        return toDto(repo.save(p));
    }

    @Override
    @Transactional
    public void rejectAndDeleteProfile(UUID userId) {
        ExpertProfile p = repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        repo.deleteById(p.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public long countVerified() {
        return repo.countByVerificationStatus(VerificationStatus.VERIFIED);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(VerificationStatus status) {
        return repo.countByVerificationStatus(status);
    }

    private static ExpertAdminProfileDto toDto(ExpertProfile p) {
        return new ExpertAdminProfileDto(
                p.getId(),
                p.getUserId(),
                p.getHeadline(),
                p.getBio(),
                Set.copyOf(p.getExpertiseTags()),
                p.getVerificationStatus(),
                p.getVerifiedAt(),
                p.getVerifiedBy(),
                p.getAppliedAt()
        );
    }
}
