package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
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
            MatchResultType type = cached.get(0).getResultType();
            List<GroupRecommendationDto> groups = cached.stream()
                    .map(c -> toDto(c.getGroupId(), c.getMatchScore(), c.getResultType(), userId))
                    .toList();
            return new RecommendationsResponse(type.name(), reliability, groups);
        }

        return computeLive(userId, courseId, profile, reliability);
    }

    private RecommendationsResponse computeLive(UUID userId, UUID courseId,
                                                 UserProfile profile, double reliability) {
        List<UUID> userCourseIds;
        if (courseId != null) {
            userCourseIds = List.of(courseId);
        } else {
            userCourseIds = groupInternalService.getGroupsForUser(userId).stream()
                    .map(s -> s.courseId())
                    .distinct()
                    .toList();
        }

        List<UUID> candidateIds = userCourseIds.isEmpty()
                ? groupInternalService.getTopPublicGroupIds(userId, props.recommendationLimit())
                : groupInternalService.getCandidateGroupIds(
                        userCourseIds, userId, props.candidateLimitPerCourse());

        if (candidateIds.isEmpty()) {
            return new RecommendationsResponse(MatchResultType.TRENDING.name(), reliability, List.of());
        }

        List<GroupProfile> groupProfiles = groupProfileRepository.findAllByGroupIdIn(candidateIds);
        boolean useTrending = reliability < props.credibilityThreshold() || groupProfiles.isEmpty();
        int daysActive = (int) ChronoUnit.DAYS.between(profile.getUpdatedAt(), Instant.now());
        double[] userFinal = MatchingScorer.userFinalScores(profile, daysActive, props);

        List<GroupRecommendationDto> groups;
        MatchResultType type;
        if (useTrending) {
            type = MatchResultType.TRENDING;
            groups = groupProfiles.stream()
                    .sorted((a, b) -> Integer.compare(b.getMemberCount(), a.getMemberCount()))
                    .limit(props.recommendationLimit())
                    .map(gp -> toDto(gp.getGroupId(), gp.getMemberCount() / 100.0, type, userId))
                    .toList();
        } else {
            type = MatchResultType.MATCHED;
            groups = groupProfiles.stream()
                    .map(gp -> {
                        double score = MatchingScorer.matchPercent(userFinal, gp, reliability, props) / 100.0;
                        return toDto(gp.getGroupId(), score, type, userId);
                    })
                    .sorted((a, b) -> Double.compare(
                            b.matchPercent() != null ? b.matchPercent() : 0,
                            a.matchPercent() != null ? a.matchPercent() : 0))
                    .limit(props.recommendationLimit())
                    .toList();
        }

        eventPublisher.publishEvent(new BuildUserMatchCacheEvent(userId));
        return new RecommendationsResponse(type.name(), reliability, groups);
    }

    private GroupRecommendationDto toDto(UUID groupId, double rawScore, MatchResultType type, UUID userId) {
        String groupName = groupInternalService.getGroupName(groupId).orElse("Unknown");
        boolean alreadyMember = groupInternalService.isMember(groupId, userId);
        Integer matchPercent = type == MatchResultType.MATCHED ? (int) Math.round(rawScore * 100) : null;
        int memberCount = groupProfileRepository.findByGroupId(groupId)
                .map(GroupProfile::getMemberCount)
                .orElse(0);
        return new GroupRecommendationDto(groupId, groupName, matchPercent, memberCount, alreadyMember);
    }
}
