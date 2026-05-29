package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminOverviewDtos;
import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.catalog.internal.CatalogAdminInternalService;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.expert.internal.ExpertAdminInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupAdminInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    private static final Duration ONE_WEEK = Duration.ofDays(7);

    private final AuthInternalService authInternalService;
    private final GroupAdminInternalService groupAdmin;
    private final CatalogAdminInternalService catalogAdmin;
    private final ExpertAdminInternalService expertAdmin;
    private final TimeProvider timeProvider;

    public AdminOverviewDtos.Overview build() {
        Map<UserRole, Long> roleDist = authInternalService.countByRoleAll();
        long totalUsers = roleDist.values().stream().mapToLong(Long::longValue).sum();
        long totalGroups = groupAdmin.countTotalGroups();
        long totalCourses = catalogAdmin.countCourses();
        long verifiedExperts = expertAdmin.countVerified();

        Instant now = timeProvider.now();
        Instant oneWeekAgo = now.minus(ONE_WEEK);
        Instant twoWeeksAgo = now.minus(ONE_WEEK.multipliedBy(2));

        long newThisWeek = authInternalService.countCreatedAfter(oneWeekAgo);
        long createdSince2w = authInternalService.countCreatedAfter(twoWeeksAgo);
        long newLastWeek = Math.max(0, createdSince2w - newThisWeek);
        long delta = newThisWeek - newLastWeek;

        var kpis = new AdminOverviewDtos.Kpis(
                totalUsers, totalGroups, totalCourses, verifiedExperts,
                newThisWeek, newLastWeek, delta
        );

        List<AdminOverviewDtos.ActivityEntry> activity = buildRecentActivity(10);
        return new AdminOverviewDtos.Overview(kpis, roleDist, activity);
    }

    private List<AdminOverviewDtos.ActivityEntry> buildRecentActivity(int limit) {
        List<AdminOverviewDtos.ActivityEntry> all = new ArrayList<>();
        authInternalService.findRecentForAdmin(limit).forEach(u ->
                all.add(new AdminOverviewDtos.ActivityEntry(
                        AdminOverviewDtos.ActivityKind.USER_REGISTERED,
                        u.id(),
                        u.displayName(),
                        u.createdAt()
                )));
        groupAdmin.findRecentGroups(limit).forEach(g ->
                all.add(new AdminOverviewDtos.ActivityEntry(
                        AdminOverviewDtos.ActivityKind.GROUP_CREATED,
                        g.id(),
                        g.name(),
                        g.createdAt()
                )));
        catalogAdmin.findRecentCourses(limit).forEach(c ->
                all.add(new AdminOverviewDtos.ActivityEntry(
                        AdminOverviewDtos.ActivityKind.COURSE_CREATED,
                        c.id(),
                        c.code() + " — " + c.name(),
                        c.createdAt()
                )));
        return all.stream()
                .sorted(Comparator.comparing(AdminOverviewDtos.ActivityEntry::at).reversed())
                .limit(limit)
                .toList();
    }
}
