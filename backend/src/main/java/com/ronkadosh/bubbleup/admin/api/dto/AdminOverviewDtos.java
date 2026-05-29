package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.common.context.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminOverviewDtos {
    private AdminOverviewDtos() {}

    public record Overview(
            Kpis kpis,
            Map<UserRole, Long> roleDistribution,
            List<ActivityEntry> recentActivity
    ) {}

    public record Kpis(
            long totalUsers,
            long totalGroups,
            long totalCourses,
            long verifiedExperts,
            long newUsersThisWeek,
            long newUsersLastWeek,
            long weekOverWeekDelta
    ) {}

    public record ActivityEntry(
            ActivityKind kind,
            UUID id,
            String label,
            Instant at
    ) {}

    public enum ActivityKind {
        USER_REGISTERED,
        GROUP_CREATED,
        COURSE_CREATED
    }
}
