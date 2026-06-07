package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminExpertDtos;
import com.ronkadosh.bubbleup.admin.audit.AdminAuditService;
import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.expert.internal.ExpertAdminInternalService;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminExpertVerificationService {

    private final ExpertAdminInternalService expertAdmin;
    private final AuthInternalService authInternalService;
    private final AdminAuditService audit;
    private final AdminGuards guards;

    public List<AdminExpertDtos.ExpertResponse> listByStatus(VerificationStatus status) {
        return expertAdmin.listByStatus(status).stream()
                .map(AdminExpertDtos.ExpertResponse::from)
                .toList();
    }

    public AdminExpertDtos.ExpertResponse getByUser(UUID userId) {
        return AdminExpertDtos.ExpertResponse.from(expertAdmin.getByUser(userId));
    }

    public AdminExpertDtos.ExpertResponse verify(UUID userId, AdminExpertDtos.VerifyRequest req, CurrentUser me) {
        guards.requireNotSelf(userId, me);
        var expert = AdminExpertDtos.ExpertResponse.from(expertAdmin.verify(userId, me.id()));
        audit.record("EXPERT_VERIFIED", "USER", userId, req.reason(), null);
        return expert;
    }

    public void reject(UUID userId, AdminExpertDtos.RejectRequest req, CurrentUser me) {
        guards.requireReason(req.reason());
        guards.requireNotSelf(userId, me);
        expertAdmin.rejectAndDeleteProfile(userId);
        authInternalService.demoteToStudent(userId);
        audit.record("EXPERT_REJECTED", "USER", userId, req.reason(), null);
    }

    public AdminExpertDtos.ExpertResponse revoke(UUID userId, AdminExpertDtos.RevokeRequest req, CurrentUser me) {
        guards.requireReason(req.reason());
        guards.requireNotSelf(userId, me);
        var expert = AdminExpertDtos.ExpertResponse.from(expertAdmin.revoke(userId));
        audit.record("EXPERT_REVOKED", "USER", userId, req.reason(), null);
        return expert;
    }
}
