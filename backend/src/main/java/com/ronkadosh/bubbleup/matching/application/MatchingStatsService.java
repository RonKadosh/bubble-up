package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats.ConfidenceBucket;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats.Coverage;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats.RoleCount;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats.RoleScore;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats.TopGroup;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.UserProfile;
import com.ronkadosh.bubbleup.matching.persistence.GroupProfileRepository;
import com.ronkadosh.bubbleup.matching.persistence.QuizResponseRepository;
import com.ronkadosh.bubbleup.matching.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the aggregate, identity-free population stats for the admin Matching panel.
 * A pure read over {@code user_profiles} + {@code group_profiles} — low volume, so it
 * loads and aggregates in memory rather than maintaining a rollup. No individual user
 * vectors leave this service; only population aggregates and per-group profiles.
 */
@Service
@RequiredArgsConstructor
public class MatchingStatsService {

    static final String[] ROLE_KEYS = {
            "LEADER", "PLANNER", "EXPERT", "CREATIVE", "COMMUNICATOR", "TEAM_PLAYER", "CHALLENGER"
    };
    private static final double GROUP_CONF_HIGH = 0.6;
    private static final double GROUP_CONF_MED = 0.3;
    private static final int TOP_GROUPS_LIMIT = 6;

    private final UserProfileRepository userProfileRepository;
    private final GroupProfileRepository groupProfileRepository;
    private final QuizResponseRepository quizResponseRepository;
    private final GroupInternalService groupInternalService;
    private final MatchingProperties props;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public MatchingStats build() {
        Instant now = timeProvider.now();
        List<UserProfile> users = userProfileRepository.findAll();

        long[] dominantCounts = new long[7];
        double[] roleSums = new double[7];
        long withCharacter = 0;
        long matchedReady = 0;
        double confSum = 0;

        for (UserProfile p : users) {
            double conf = MatchingScorer.userConfidence(
                    p.getAnsweredQuestions(),
                    MatchingScorer.behaviorEvidence(p.getBehaviorCounts(), props), props);
            confSum += conf;
            if (conf >= props.matchedDisplayThreshold()) matchedReady++;

            int daysActive = (int) ChronoUnit.DAYS.between(p.getUpdatedAt(), now);
            double[] v = MatchingScorer.userFinalScores(p, daysActive, props);
            int dom = argmax(v);
            if (dom >= 0) {
                dominantCounts[dom]++;
                for (int r = 0; r < 7; r++) roleSums[r] += v[r];
                withCharacter++;
            }
        }

        List<RoleCount> dominantRoles = new ArrayList<>(7);
        List<RoleScore> populationProfile = new ArrayList<>(7);
        for (int r = 0; r < 7; r++) {
            dominantRoles.add(new RoleCount(ROLE_KEYS[r], dominantCounts[r]));
            populationProfile.add(new RoleScore(ROLE_KEYS[r],
                    withCharacter == 0 ? 0.0 : roleSums[r] / withCharacter));
        }

        Coverage coverage = new Coverage(
                users.size(), matchedReady,
                users.isEmpty() ? 0.0 : confSum / users.size(),
                quizResponseRepository.count(),
                groupProfileRepository.count());

        List<GroupProfile> groups = groupProfileRepository.findAll();
        long high = 0, med = 0, low = 0;
        for (GroupProfile g : groups) {
            double c = g.getGroupProfileConfidence();
            if (c >= GROUP_CONF_HIGH) high++;
            else if (c >= GROUP_CONF_MED) med++;
            else low++;
        }
        List<ConfidenceBucket> groupConfidence = List.of(
                new ConfidenceBucket("HIGH", high),
                new ConfidenceBucket("MEDIUM", med),
                new ConfidenceBucket("LOW", low));

        List<TopGroup> topGroups = groups.stream()
                .sorted(Comparator.comparingLong(
                        (GroupProfile g) -> g.getTrendingActivityCount() + g.getMemberCount()).reversed())
                .limit(TOP_GROUPS_LIMIT)
                .map(g -> {
                    int dom = argmaxGroup(g);
                    return new TopGroup(
                            groupInternalService.getGroupName(g.getGroupId()).orElse("—"),
                            g.getMemberCount(),
                            g.getGroupProfileConfidence(),
                            dom >= 0 ? ROLE_KEYS[dom] : "NONE");
                })
                .toList();

        return new MatchingStats(coverage, dominantRoles, populationProfile, groupConfidence, topGroups);
    }

    private static int argmax(double[] v) {
        int best = -1;
        double max = 0.0;
        for (int r = 0; r < v.length; r++) {
            if (v[r] > max) { max = v[r]; best = r; }
        }
        return best;
    }

    private static int argmaxGroup(GroupProfile g) {
        int best = -1;
        double max = 0.0;
        for (int r = 0; r < 7; r++) {
            if (g.getAvgScore(r) > max) { max = g.getAvgScore(r); best = r; }
        }
        return best;
    }
}
