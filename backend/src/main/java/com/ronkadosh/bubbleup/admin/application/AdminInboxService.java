package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.expert.internal.ExpertAdminInternalService;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import com.ronkadosh.bubbleup.report.internal.ReportAdminInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pending-work counters that drive the admin tab badges. One cheap call the
 * admin shell polls on load instead of fetching each inbox just to size a badge.
 */
@Service
@RequiredArgsConstructor
public class AdminInboxService {

    private final ExpertAdminInternalService expertAdmin;
    private final ReportAdminInternalService reportAdmin;

    public InboxCounts counts() {
        return new InboxCounts(
                expertAdmin.countByStatus(VerificationStatus.PENDING),
                reportAdmin.countPending()
        );
    }

    public record InboxCounts(long pendingExpertRequests, long pendingReports) {}
}
