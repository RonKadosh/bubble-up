package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.matching.model.*;
import com.ronkadosh.bubbleup.matching.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchingCommandService {

    private final UserProfileRepository userProfileRepository;
    private final GroupProfileRepository groupProfileRepository;
    private final UserMatchCacheRepository matchCacheRepository;
    private final QuizResponseRepository quizResponseRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAnswerOptionRepository answerOptionRepository;
    private final GroupInternalService groupInternalService;
    private final MatchingProperties props;
    private final TimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Self-proxy. Required so {@link #recomputeGroupProfile(UUID)} can invoke
     * the {@code @Transactional} {@link #doRecomputeGroupProfile(UUID)} method
     * through the AOP proxy — direct {@code this.}-calls would bypass it.
     * Lazy to break the trivial self-cycle at construction time.
     */
    private MatchingCommandService self;

    @Autowired
    public void setSelf(@Lazy MatchingCommandService self) {
        this.self = self;
    }

    @Transactional
    public void submitAnswer(UUID userId, UUID questionId, UUID answerId) {
        if (!quizQuestionRepository.existsById(questionId)) {
            throw new AppException(ErrorCode.QUIZ_QUESTION_NOT_FOUND);
        }
        boolean validAnswer = answerOptionRepository.findAllByQuestionId(questionId)
                .stream().anyMatch(opt -> opt.getId().equals(answerId));
        if (!validAnswer) {
            throw new AppException(ErrorCode.QUIZ_ANSWER_INVALID);
        }
        quizResponseRepository.findByUserIdAndQuestionId(userId, questionId)
                .ifPresentOrElse(
                        existing -> {
                            existing.setAnswerId(answerId);
                            existing.setRespondedAt(timeProvider.now());
                            quizResponseRepository.save(existing);
                        },
                        () -> quizResponseRepository.save(QuizResponse.builder()
                                .userId(userId)
                                .questionId(questionId)
                                .answerId(answerId)
                                .respondedAt(timeProvider.now())
                                .build())
                );
        eventPublisher.publishEvent(new UserBehaviorEvent(userId, BehaviorEventType.USER_ANSWERED_QUIZ_QUESTION));
    }

    @Transactional
    public UserProfile getOrCreateUserProfile(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile profile = UserProfile.builder()
                            .userId(userId)
                            .updatedAt(timeProvider.now())
                            .build();
                    return userProfileRepository.save(profile);
                });
    }

    @Transactional
    public void recomputeUserQuizProfile(UUID userId) {
        UserProfile profile = getOrCreateUserProfile(userId);
        List<QuizResponse> responses = quizResponseRepository.findAllByUserId(userId);

        for (int r = 0; r < 7; r++) profile.setQuizScore(r, 0.0);

        for (QuizResponse response : responses) {
            answerOptionRepository.findById(response.getAnswerId()).ifPresent(option -> {
                double[] weights = option.weightsArray();
                for (int r = 0; r < 7; r++) {
                    profile.setQuizScore(r, Math.min(1.0, profile.getQuizScore(r) + weights[r]));
                }
            });
        }
        profile.setAnsweredQuestions(responses.size());
        profile.setUpdatedAt(timeProvider.now());
        userProfileRepository.save(profile);
    }

    @Transactional
    public void applyBehaviorDelta(UUID userId, BehaviorEventType eventType) {
        UserProfile profile = getOrCreateUserProfile(userId);
        switch (eventType) {
            case CREATED_GROUP          -> profile.addBehaviorDelta(0, props.deltas().createdGroupLeader());
            case ADDED_MEMBER           -> profile.addBehaviorDelta(0, props.deltas().addedMemberLeader());
            case CREATED_CALENDAR_EVENT -> profile.addBehaviorDelta(1, props.deltas().createdCalendarEventPlanner());
            case UPLOADED_FILE          -> profile.addBehaviorDelta(2, props.deltas().uploadedFileExpert());
            case SENT_MESSAGE           -> profile.addBehaviorDelta(4, props.deltas().sentMessageCommunicator());
            default -> { /* future placeholder — no delta configured */ }
        }
        profile.setUpdatedAt(timeProvider.now());
        userProfileRepository.save(profile);
    }

    /**
     * Entry point — wraps the transactional recompute with a one-shot retry on
     * unique-key conflict. Concurrent async handlers (membership change + user
     * behavior often fire for the same group) can both see "no GroupProfile
     * row yet" and race on INSERT; the loser gets a constraint violation. The
     * retry's {@code findByGroupId} will then succeed (the winner's row is now
     * visible) and the body updates in place.
     *
     * <p>Not {@code @Transactional} — must be outside the tx boundary so the
     * retry runs in a fresh transaction; the failed one is already
     * rollback-only.
     */
    public void recomputeGroupProfile(UUID groupId) {
        try {
            self.doRecomputeGroupProfile(groupId);
        } catch (DataIntegrityViolationException e) {
            self.doRecomputeGroupProfile(groupId);
        }
    }

    @Transactional
    public void doRecomputeGroupProfile(UUID groupId) {
        List<UUID> memberIds = groupInternalService.getMemberUserIds(groupId);

        List<UserProfile> memberProfiles = memberIds.stream()
                .map(id -> userProfileRepository.findByUserId(id))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        GroupProfile groupProfile = groupProfileRepository.findByGroupId(groupId)
                .orElseGet(() -> GroupProfile.builder().groupId(groupId).build());

        int count = memberProfiles.size();
        if (count == 0) {
            for (int r = 0; r < 7; r++) groupProfile.setAvgScore(r, 0.0);
            groupProfile.setMemberCount(0);
        } else {
            for (int r = 0; r < 7; r++) {
                final int role = r;
                double avg = memberProfiles.stream()
                        .mapToDouble(p -> MatchingScorer.finalScore(role, p, 0, props))
                        .average()
                        .orElse(0.0);
                groupProfile.setAvgScore(r, avg);
            }
            groupProfile.setMemberCount(count);
        }
        groupProfile.setUpdatedAt(timeProvider.now());
        groupProfileRepository.save(groupProfile);
    }

    /**
     * Not {@code @Transactional} on purpose — each per-group recompute runs in
     * its own transaction via {@link #recomputeGroupProfile(UUID)} so a
     * conflict on one group doesn't poison the rest of the loop.
     */
    public void recomputeGroupProfilesForUser(UUID userId) {
        groupInternalService.getGroupsForUser(userId)
                .forEach(summary -> recomputeGroupProfile(summary.id()));
    }

    @Transactional
    public void refreshUserMatchCache(UUID userId) {
        UserProfile profile = getOrCreateUserProfile(userId);
        var now = timeProvider.now();

        List<UUID> userCourseIds = groupInternalService.getGroupsForUser(userId).stream()
                .map(s -> s.courseId())
                .distinct()
                .toList();

        List<UUID> candidateIds = userCourseIds.isEmpty()
                ? groupInternalService.getTopPublicGroupIds(userId, props.recommendationLimit())
                : groupInternalService.getCandidateGroupIds(userCourseIds, userId, props.candidateLimitPerCourse());

        if (candidateIds.isEmpty()) {
            matchCacheRepository.deleteAllByUserId(userId);
            return;
        }

        List<GroupProfile> groupProfiles = groupProfileRepository.findAllByGroupIdIn(candidateIds);
        long totalActive = quizQuestionRepository.countByActive(true);
        double reliability = totalActive == 0 ? 0.0 : (double) profile.getAnsweredQuestions() / totalActive;
        boolean useTrending = reliability < props.credibilityThreshold() || groupProfiles.isEmpty();

        int daysActive = (int) ChronoUnit.DAYS.between(profile.getUpdatedAt(), now);

        for (GroupProfile gp : groupProfiles) {
            UUID courseId = groupInternalService.getCourseIdForGroup(gp.getGroupId())
                    .orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));

            double matchScore;
            MatchResultType resultType;
            if (useTrending) {
                matchScore = gp.getMemberCount() / 100.0; // normalized for ordering
                resultType = MatchResultType.TRENDING;
            } else {
                double[] userFinal = MatchingScorer.userFinalScores(profile, daysActive, props);
                matchScore = MatchingScorer.matchPercent(userFinal, gp, reliability, props) / 100.0;
                resultType = MatchResultType.MATCHED;
            }

            matchCacheRepository.findByUserIdAndGroupId(userId, gp.getGroupId())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setMatchScore(matchScore);
                                existing.setResultType(resultType);
                                existing.setCourseId(courseId);
                                existing.setCachedAt(now);
                                matchCacheRepository.save(existing);
                            },
                            () -> matchCacheRepository.save(UserMatchCache.builder()
                                    .userId(userId)
                                    .groupId(gp.getGroupId())
                                    .courseId(courseId)
                                    .matchScore(matchScore)
                                    .resultType(resultType)
                                    .cachedAt(now)
                                    .build())
                    );
        }

        matchCacheRepository.deleteStaleByUserId(userId, candidateIds);
    }

    @Transactional
    public void invalidateCacheForGroup(UUID groupId) {
        matchCacheRepository.deleteByGroupId(groupId);
    }
}
