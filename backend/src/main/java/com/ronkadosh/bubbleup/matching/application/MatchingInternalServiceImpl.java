package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.matching.api.dto.RecommendationsResponse;
import com.ronkadosh.bubbleup.matching.internal.MatchingInternalService;
import com.ronkadosh.bubbleup.matching.internal.dto.GroupRecommendation;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchSnapshot;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchingReliability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchingInternalServiceImpl implements MatchingInternalService {

    private final MatchingQueryService queryService;

    @Override
    public List<GroupRecommendation> getRecommendations(UUID userId, UUID courseId) {
        RecommendationsResponse res = queryService.getRecommendations(userId, courseId);
        return res.groups().stream()
                .map(g -> new GroupRecommendation(
                        g.groupId(),
                        g.groupName(),
                        g.courseCode(),
                        g.courseName(),
                        g.matchPercent(),
                        g.memberCount(),
                        g.alreadyMember(),
                        g.displayMode(),
                        g.reasonLabels()))
                .toList();
    }

    @Override
    public MatchingReliability getReliability(UUID userId) {
        return queryService.getReliability(userId);
    }

    @Override
    public MatchSnapshot matchSnapshotFor(UUID userId, UUID groupId) {
        return queryService.matchSnapshotFor(userId, groupId);
    }
}
