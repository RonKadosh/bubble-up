package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.enrollment.internal.EnrollmentInternalService;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final EnrollmentInternalService enrollmentInternalService;
    private final ChatInternalService chatInternalService;
    private final CalendarInternalService calendarInternalService;
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

    /**
     * Demo/seed helper: answers up to {@code count} of the user's unanswered questions,
     * each time picking the option that most strongly expresses {@code targetRole}
     * (0=Leader, 1=Planner, 2=Expert, 3=Creative, 4=Communicator, 5=TeamPlayer,
     * 6=Challenger), so the resulting character vector deliberately leans that way.
     * Real {@code QuizResponse} rows mean the profile survives later recomputes. Lives
     * here (not in the seeder) because the answer-option weights are private to this
     * module. No cooldown is involved — that gate lives only in {@code getNextQuestion}.
     */
    @Transactional
    public void seedCharacterAnswers(UUID userId, int targetRole, int count) {
        for (int i = 0; i < count; i++) {
            List<QuizQuestion> pool = quizQuestionRepository.findUnansweredActiveByUserId(userId);
            if (pool.isEmpty()) break;
            QuizQuestion question = pool.get(0);
            List<QuizAnswerOption> options = answerOptionRepository.findAllByQuestionId(question.getId());
            if (options.isEmpty()) break;
            QuizAnswerOption best = options.get(0);
            for (QuizAnswerOption o : options) {
                if (o.weightsArray()[targetRole] > best.weightsArray()[targetRole]) best = o;
            }
            submitAnswer(userId, question.getId(), best.getId());
        }
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

        // Build the character vector as a max-normalized SHAPE, not a raw running
        // tally — so answering more questions sharpens the profile toward the
        // user's true leanings instead of creeping every role toward 1.0. Trust in
        // the shape is tracked separately by answeredQuestions → user_confidence.
        List<double[]> answerWeights = new ArrayList<>();
        for (QuizResponse response : responses) {
            answerOptionRepository.findById(response.getAnswerId())
                    .ifPresent(option -> answerWeights.add(option.weightsArray()));
        }
        double[] vector = MatchingScorer.buildQuizVector(answerWeights);
        for (int r = 0; r < 7; r++) profile.setQuizScore(r, vector[r]);

        profile.setAnsweredQuestions(responses.size());
        profile.setUpdatedAt(timeProvider.now());
        userProfileRepository.save(profile);
    }

    @Transactional
    public void recordBehaviorEvent(UUID userId, BehaviorEventType eventType) {
        UserProfile profile = getOrCreateUserProfile(userId);
        // Just bump the raw count — role mapping + saturation happen at read time in
        // MatchingScorer.behaviorVector, driven by app.matching.signals config.
        profile.incrementBehaviorCount(eventType);
        // Every behavior event counts toward behavior_confidence. Quiz answers do NOT
        // pass through here (they go to recomputeUserQuizProfile / answeredQuestions).
        profile.setMeaningfulBehaviorEvents(profile.getMeaningfulBehaviorEvents() + 1);
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

        int memberCount = memberIds.size();   // true count (members without a profile still count for size)
        if (memberProfiles.isEmpty()) {
            for (int r = 0; r < 7; r++) groupProfile.setAvgScore(r, 0.0);
            groupProfile.setGroupProfileConfidence(0.0);
        } else {
            // Each member's confidence weights their contribution to the group vector,
            // so a strong-but-well-known member shapes the profile more than a guess.
            // Each member's blended role vector is computed once (behavior is normalized
            // as a whole vector, so it can't be done role-by-role).
            double[] confs = new double[memberProfiles.size()];
            double[][] memberFinal = new double[memberProfiles.size()][];
            double confSum = 0;
            for (int i = 0; i < memberProfiles.size(); i++) {
                UserProfile p = memberProfiles.get(i);
                confs[i] = MatchingScorer.userConfidence(
                        p.getAnsweredQuestions(),
                        MatchingScorer.behaviorEvidence(p.getBehaviorCounts(), props), props);
                confSum += confs[i];
                memberFinal[i] = MatchingScorer.userFinalScores(p, 0, props);
            }
            for (int r = 0; r < 7; r++) {
                double avg;
                if (confSum > 0) {
                    double weighted = 0;
                    for (int i = 0; i < memberProfiles.size(); i++) {
                        weighted += memberFinal[i][r] * confs[i];
                    }
                    avg = weighted / confSum;
                } else {
                    double sum = 0;   // all members fully unknown → plain average
                    for (int i = 0; i < memberProfiles.size(); i++) sum += memberFinal[i][r];
                    avg = sum / memberProfiles.size();
                }
                groupProfile.setAvgScore(r, avg);
            }
            groupProfile.setGroupProfileConfidence(
                    MatchingScorer.groupConfidence(confs, memberCount, props));
        }
        groupProfile.setMemberCount(memberCount);

        // Trending raw signals (counts within configured windows; stored un-normalized).
        Instant now = timeProvider.now();
        MatchingProperties.Trending t = props.trending();
        Instant activitySince = now.minus(t.activityWindowDays(), ChronoUnit.DAYS);
        Instant joinSince = now.minus(t.recentJoinWindowDays(), ChronoUnit.DAYS);
        long activity = chatInternalService.countMessagesForGroupSince(groupId, activitySince)
                + groupInternalService.countFilesForGroupSince(groupId, activitySince);
        groupProfile.setTrendingActivityCount(activity);
        groupProfile.setTrendingRecentJoins(
                groupInternalService.countRecentJoinsForGroup(groupId, joinSince));
        long upcoming = calendarInternalService.countUpcomingForOwners(
                CalendarOwnerType.GROUP, List.of(groupId),
                now, now.plus(t.upcomingWindowDays(), ChronoUnit.DAYS));
        groupProfile.setTrendingUpcomingSessions((int) upcoming);

        groupProfile.setUpdatedAt(now);
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

    /**
     * Entry point — wraps the transactional refresh with a one-shot retry on
     * unique-key conflict, mirroring {@link #recomputeGroupProfile(UUID)}. With many
     * behavior events firing, two async handlers for the same user can both see "no
     * cache row yet" for a (user, group) pair and race on INSERT; the loser hits the
     * {@code user_match_cache} unique constraint. The retry's {@code findByUserIdAndGroupId}
     * then sees the winner's row and updates in place. Not {@code @Transactional} — the
     * retry must run in a fresh transaction (the failed one is rollback-only).
     */
    public void refreshUserMatchCache(UUID userId) {
        try {
            self.doRefreshUserMatchCache(userId);
        } catch (DataIntegrityViolationException e) {
            self.doRefreshUserMatchCache(userId);
        }
    }

    @Transactional
    public void doRefreshUserMatchCache(UUID userId) {
        UserProfile profile = getOrCreateUserProfile(userId);
        var now = timeProvider.now();

        // Enrollment-driven discovery: cache candidates from the user's current-term
        // offerings, mirroring MatchingQueryService.computeLive. No current-term
        // enrolments → nothing the user can join; clear the cache (no global-trending
        // fallback — that would leak ungated bubbles and diverge from the live path).
        List<UUID> offeringIds = enrollmentInternalService.enrolledOfferingIdsForCurrentTerm(userId);
        if (offeringIds.isEmpty()) {
            matchCacheRepository.deleteAllByUserId(userId);
            return;
        }

        List<UUID> candidateIds = groupInternalService.getCandidateGroupIdsByOffering(
                offeringIds, userId, props.candidateLimitPerCourse());

        if (candidateIds.isEmpty()) {
            matchCacheRepository.deleteAllByUserId(userId);
            return;
        }

        List<GroupProfile> groupProfiles = groupProfileRepository.findAllByGroupIdIn(candidateIds);
        int daysActive = (int) ChronoUnit.DAYS.between(profile.getUpdatedAt(), now);
        double[] userFinal = MatchingScorer.userFinalScores(profile, daysActive, props);
        double userConfidence = MatchingScorer.userConfidence(
                profile.getAnsweredQuestions(),
                MatchingScorer.behaviorEvidence(profile.getBehaviorCounts(), props), props);

        for (GroupProfile gp : groupProfiles) {
            UUID courseId = groupInternalService.getCourseIdForGroup(gp.getGroupId())
                    .orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            // Term-precise key: offering encodes course+term, enabling a future
            // term-rollover sweep / partition keyed off it.
            UUID offeringId = groupInternalService.getOfferingIdForGroup(gp.getGroupId())
                    .orElse(null);

            MatchingScorer.Scored s = MatchingScorer.score(userFinal, userConfidence, gp, props);

            matchCacheRepository.findByUserIdAndGroupId(userId, gp.getGroupId())
                    .ifPresentOrElse(
                            existing -> {
                                existing.setMatchScore(s.finalScore());
                                existing.setResultType(s.mode());
                                existing.setMatchingConfidence(s.matchingConfidence());
                                existing.setMatchPercent(s.matchPercent());
                                existing.setCourseId(courseId);
                                existing.setOfferingId(offeringId);
                                existing.setCachedAt(now);
                                matchCacheRepository.save(existing);
                            },
                            () -> matchCacheRepository.save(UserMatchCache.builder()
                                    .userId(userId)
                                    .groupId(gp.getGroupId())
                                    .courseId(courseId)
                                    .offeringId(offeringId)
                                    .matchScore(s.finalScore())
                                    .resultType(s.mode())
                                    .matchingConfidence(s.matchingConfidence())
                                    .matchPercent(s.matchPercent())
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
