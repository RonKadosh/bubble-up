package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.enrollment.internal.EnrollmentInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.matching.api.dto.GroupRecommendationDto;
import com.ronkadosh.bubbleup.matching.api.dto.NextQuestionResponse;
import com.ronkadosh.bubbleup.matching.api.dto.QuizOptionDto;
import com.ronkadosh.bubbleup.matching.api.dto.RecommendationsResponse;
import com.ronkadosh.bubbleup.matching.model.*;
import com.ronkadosh.bubbleup.matching.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MatchingQueryService {

    private final UserProfileRepository userProfileRepository;
    private final GroupProfileRepository groupProfileRepository;
    private final UserMatchCacheRepository matchCacheRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerOptionRepository answerOptionRepository;
    private final GroupInternalService groupInternalService;
    private final EnrollmentInternalService enrollmentInternalService;
    private final CatalogInternalService catalogInternalService;
    private final MatchingCommandService commandService;
    private final MatchingProperties props;
    private final TimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NextQuestionResponse getNextQuestion(UUID userId, String lang) {
        UserProfile profile = commandService.getOrCreateUserProfile(userId);

        if (profile.getAnsweredQuestions() >= props.quizQuestionCap()) {
            return NextQuestionResponse.exhausted();
        }

        Instant now = timeProvider.now();
        if (profile.getLastQuestionShownAt() != null) {
            Instant nextAvailableAt = profile.getLastQuestionShownAt().plus(props.quizCooldown());
            if (nextAvailableAt.isAfter(now)) {
                return NextQuestionResponse.cooldown(nextAvailableAt);
            }
        }

        List<QuizQuestion> pool = new ArrayList<>(
                quizQuestionRepository.findUnansweredActiveByUserId(userId));
        if (pool.isEmpty()) {
            return NextQuestionResponse.exhausted();
        }

        QuizQuestion question = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        profile.setLastQuestionShownAt(now);
        userProfileRepository.save(profile);

        List<QuizOptionDto> options = answerOptionRepository
                .findAllByQuestionId(question.getId())
                .stream()
                .map(opt -> new QuizOptionDto(opt.getId(), opt.localizedText(lang)))
                .toList();
        return NextQuestionResponse.of(
                question.getId(), question.localizedText(lang), options,
                now.plus(props.quizCooldown()));
    }

    @Transactional(readOnly = true)
    public RecommendationsResponse getRecommendations(UUID userId, UUID courseId) {
        if (courseId != null && !catalogInternalService.courseExists(courseId)) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }

        long totalActive = quizQuestionRepository.countByActive(true);
        UserProfile profile = commandService.getOrCreateUserProfile(userId);
        double reliability = totalActive == 0 ? 0.0
                : (double) profile.getAnsweredQuestions() / totalActive;

        List<UserMatchCache> cached = courseId != null
                ? matchCacheRepository.findByUserIdAndCourseIdOrderByMatchScoreDesc(
                        userId, courseId, PageRequest.of(0, props.recommendationLimit()))
                : matchCacheRepository.findByUserIdOrderByMatchScoreDesc(
                        userId, PageRequest.of(0, props.recommendationLimit()));

        if (!cached.isEmpty()) {
            List<GroupRecommendationDto> groups = cached.stream()
                    .map(c -> toDto(c.getGroupId(), c.getMatchPercent(), c.getResultType(),
                            c.getMatchingConfidence(), userId))
                    .toList();
            return new RecommendationsResponse(summaryType(groups), reliability, groups);
        }

        return computeLive(userId, courseId, profile, reliability);
    }

    private RecommendationsResponse computeLive(UUID userId, UUID courseId,
                                                 UserProfile profile, double reliability) {
        List<UUID> userCourseIds;
        if (courseId != null) {
            userCourseIds = List.of(courseId);
        } else {
            // "Your courses" for discovery = what you're enrolled in this term,
            // not the courses of bubbles you already joined. This lets a freshly
            // enrolled student get course-relevant recommendations before joining
            // anything; an unenrolled user falls through to global trending below.
            userCourseIds = enrollmentInternalService.enrolledCourseIdsForCurrentTerm(userId);
        }

        List<UUID> candidateIds = userCourseIds.isEmpty()
                ? groupInternalService.getTopPublicGroupIds(userId, props.recommendationLimit())
                : groupInternalService.getCandidateGroupIds(
                        userCourseIds, userId, props.candidateLimitPerCourse());

        if (candidateIds.isEmpty()) {
            return new RecommendationsResponse(MatchResultType.TRENDING.name(), reliability, List.of());
        }

        List<GroupProfile> groupProfiles = groupProfileRepository.findAllByGroupIdIn(candidateIds);
        int daysActive = (int) ChronoUnit.DAYS.between(profile.getUpdatedAt(), Instant.now());
        double[] userFinal = MatchingScorer.userFinalScores(profile, daysActive, props);
        double userConfidence = MatchingScorer.userConfidence(
                profile.getAnsweredQuestions(), profile.getMeaningfulBehaviorEvents(), props);

        // One continuous pipeline: every candidate is blended (matching ⇄ trending by
        // confidence) and ranked by final_score; the MATCHED/TRENDING label is per-rec.
        List<GroupRecommendationDto> groups = groupProfiles.stream()
                .map(gp -> new ScoredGroup(gp, MatchingScorer.score(userFinal, userConfidence, gp, props)))
                .sorted((a, b) -> Double.compare(b.scored().finalScore(), a.scored().finalScore()))
                .limit(props.recommendationLimit())
                .map(sg -> toDto(sg.profile().getGroupId(), sg.scored().matchPercent(),
                        sg.scored().mode(), sg.scored().matchingConfidence(), userId))
                .toList();

        eventPublisher.publishEvent(new BuildUserMatchCacheEvent(userId));
        return new RecommendationsResponse(summaryType(groups), reliability, groups);
    }

    private record ScoredGroup(GroupProfile profile, MatchingScorer.Scored scored) {}

    private GroupRecommendationDto toDto(UUID groupId, Integer matchPercent, MatchResultType mode,
                                         double matchingConfidence, UUID userId) {
        String groupName = groupInternalService.getGroupName(groupId).orElse("Unknown");
        boolean alreadyMember = groupInternalService.isMember(groupId, userId);
        Optional<GroupProfile> gp = groupProfileRepository.findByGroupId(groupId);
        int memberCount = gp.map(GroupProfile::getMemberCount).orElse(0);
        List<String> reasons = (mode == MatchResultType.TRENDING && gp.isPresent())
                ? trendingReasons(gp.get())
                : List.of();
        return new GroupRecommendationDto(groupId, groupName, matchPercent, memberCount,
                alreadyMember, mode.name(), matchingConfidence, reasons);
    }

    /** The top 1–2 trending signals driving a bubble, as machine codes the client localizes. */
    private List<String> trendingReasons(GroupProfile gp) {
        MatchingProperties.Trending t = props.trending();
        record Sig(String code, double contribution) {}
        List<Sig> sigs = List.of(
                new Sig("TRENDING_ACTIVE",   t.activityWeight()    * norm(gp.getTrendingActivityCount(), t.activityCap())),
                new Sig("TRENDING_GROWING",  t.recentJoinWeight()  * norm(gp.getTrendingRecentJoins(), t.recentJoinCap())),
                new Sig("TRENDING_POPULAR",  t.memberCountWeight() * norm(gp.getMemberCount(), t.memberCountCap())),
                new Sig("TRENDING_UPCOMING", t.upcomingWeight()    * norm(gp.getTrendingUpcomingSessions(), t.upcomingCap())));
        return sigs.stream()
                .filter(s -> s.contribution() > 1e-9)
                .sorted((a, b) -> Double.compare(b.contribution(), a.contribution()))
                .limit(2)
                .map(Sig::code)
                .toList();
    }

    private static double norm(double raw, double cap) {
        return cap <= 0 ? 0.0 : Math.min(raw / cap, 1.0);
    }

    private static String summaryType(List<GroupRecommendationDto> groups) {
        boolean anyMatched = groups.stream()
                .anyMatch(g -> MatchResultType.MATCHED.name().equals(g.displayMode()));
        return anyMatched ? MatchResultType.MATCHED.name() : MatchResultType.TRENDING.name();
    }
}
