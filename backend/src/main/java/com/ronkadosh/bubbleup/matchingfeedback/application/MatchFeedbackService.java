package com.ronkadosh.bubbleup.matchingfeedback.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.events.RecommendationsShownEvent;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.matching.internal.MatchingInternalService;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchSnapshot;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.MatchFeedbackAnalytics;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.MatchFeedbackAnalytics.ModeFunnel;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.MatchFeedbackAnalytics.PercentBucket;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.MatchFeedbackAnalytics.RatingTotals;
import com.ronkadosh.bubbleup.matchingfeedback.model.FeedbackRating;
import com.ronkadosh.bubbleup.matchingfeedback.model.MatchFeedback;
import com.ronkadosh.bubbleup.matchingfeedback.persistence.MatchFeedbackRepository;
import com.ronkadosh.bubbleup.matchingfeedback.persistence.MatchFeedbackRepository.PercentRow;
import com.ronkadosh.bubbleup.matchingfeedback.persistence.MatchFeedbackRepository.RatingCountRow;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Accumulates matching feedback (impression / join / explicit rating) into the
 * {@code match_feedback} upsert table and builds the admin reflection aggregates.
 * Each public entry point retries once on the unique-key race (two concurrent upserts
 * of the same (user, group) row) via the self-proxy, mirroring
 * {@code MatchingCommandService.recomputeGroupProfile}.
 */
@Service
@RequiredArgsConstructor
public class MatchFeedbackService {

    /** Match-% band edges (upper-exclusive except the last); see {@link #bucketLabel}. */
    private static final int[] BUCKET_EDGES = {0, 60, 75, 90, 101};

    private final MatchFeedbackRepository repo;
    private final GroupInternalService groupInternalService;
    private final MatchingInternalService matchingInternalService;
    private final TimeProvider timeProvider;

    private MatchFeedbackService self;

    @Autowired
    public void setSelf(@Lazy MatchFeedbackService self) {
        this.self = self;
    }

    // ── Accumulation ─────────────────────────────────────────────────────────

    public void recordImpressions(UUID userId, List<RecommendationsShownEvent.Shown> items) {
        for (RecommendationsShownEvent.Shown item : items) {
            withRetry(() -> self.upsertImpression(userId, item));
        }
    }

    public void recordJoin(UUID userId, UUID groupId) {
        withRetry(() -> self.upsertJoin(userId, groupId));
    }

    public void recordRating(UUID userId, UUID groupId, FeedbackRating rating) {
        if (!groupInternalService.groupExists(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        withRetry(() -> self.upsertRating(userId, groupId, rating));
    }

    @Transactional
    public void upsertImpression(UUID userId, RecommendationsShownEvent.Shown item) {
        Instant now = timeProvider.now();
        MatchFeedback f = loadOrNew(userId, item.groupId(), now);
        f.setShownCount(f.getShownCount() + 1);
        f.setLastShownAt(now);
        f.setLastDisplayMode(item.displayMode());
        f.setLastMatchPercent(item.matchPercent());
        f.setLastMatchingConfidence(item.matchingConfidence());
        f.setUpdatedAt(now);
        repo.save(f);
    }

    @Transactional
    public void upsertJoin(UUID userId, UUID groupId) {
        // Only mark joins on bubbles we actually recommended — organic joins (no prior
        // impression row) aren't recommendation outcomes, so they're ignored.
        repo.findByUserIdAndGroupId(userId, groupId).ifPresent(f -> {
            if (f.getJoinedAt() == null) {
                Instant now = timeProvider.now();
                f.setJoinedAt(now);
                f.setUpdatedAt(now);
                repo.save(f);
            }
        });
    }

    @Transactional
    public void upsertRating(UUID userId, UUID groupId, FeedbackRating rating) {
        Instant now = timeProvider.now();
        MatchFeedback f = loadOrNew(userId, groupId, now);
        if (f.getRating() != null) return;   // once per user per group — keep the first vote
        // Stamp the rating with the user's match % at click time — cached if a candidate,
        // else scored live (members aren't in their own candidate cache).
        MatchSnapshot snap = matchingInternalService.matchSnapshotFor(userId, groupId);
        f.setRating(rating);
        f.setRatingMatchPercent(snap.matchPercent());
        f.setRatedAt(now);
        f.setUpdatedAt(now);
        repo.save(f);
    }

    /** The caller's existing fit rating for a group, if any — so the UI asks only once. */
    @Transactional(readOnly = true)
    public Optional<FeedbackRating> getRating(UUID userId, UUID groupId) {
        return repo.findByUserIdAndGroupId(userId, groupId).map(MatchFeedback::getRating);
    }

    private MatchFeedback loadOrNew(UUID userId, UUID groupId, Instant now) {
        return repo.findByUserIdAndGroupId(userId, groupId).orElseGet(() ->
                MatchFeedback.builder()
                        .userId(userId)
                        .groupId(groupId)
                        .courseId(groupInternalService.getCourseIdForGroup(groupId).orElse(null))
                        .firstShownAt(now)
                        .updatedAt(now)
                        .build());
    }

    private void withRetry(Runnable upsert) {
        try {
            upsert.run();
        } catch (DataIntegrityViolationException e) {
            upsert.run();   // loser of an insert race: the retry finds the winner's row and updates it
        }
    }

    // ── Reflection ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MatchFeedbackAnalytics buildAnalytics() {
        List<ModeFunnel> funnel = repo.funnelByMode().stream()
                .map(r -> new ModeFunnel(r.getMode(), r.getShown(), r.getJoined(),
                        rate(r.getJoined(), r.getShown())))
                .toList();

        List<PercentBucket> buckets = buildBuckets(repo.shownWithPercent());

        long good = 0, bad = 0;
        for (RatingCountRow r : repo.ratingCounts()) {
            if (r.getRating() == FeedbackRating.GOOD_FIT) good = r.getCount();
            else if (r.getRating() == FeedbackRating.BAD_FIT) bad = r.getCount();
        }

        return new MatchFeedbackAnalytics(funnel, buckets, new RatingTotals(good, bad));
    }

    private List<PercentBucket> buildBuckets(List<PercentRow> rows) {
        int n = BUCKET_EDGES.length - 1;
        long[] shown = new long[n], joined = new long[n], good = new long[n], bad = new long[n];
        for (PercentRow row : rows) {
            int b = bucketIndex(row.getMatchPercent());
            if (b < 0) continue;
            shown[b]++;
            if (row.getJoinedAt() != null) joined[b]++;
            if (row.getRating() == FeedbackRating.GOOD_FIT) good[b]++;
            else if (row.getRating() == FeedbackRating.BAD_FIT) bad[b]++;
        }
        List<PercentBucket> out = new ArrayList<>(n);
        for (int b = 0; b < n; b++) {
            out.add(new PercentBucket(bucketLabel(b), shown[b], joined[b],
                    rate(joined[b], shown[b]), good[b], bad[b]));
        }
        return out;
    }

    private static int bucketIndex(Integer percent) {
        if (percent == null) return -1;
        for (int b = 0; b < BUCKET_EDGES.length - 1; b++) {
            if (percent >= BUCKET_EDGES[b] && percent < BUCKET_EDGES[b + 1]) return b;
        }
        return -1;
    }

    private static String bucketLabel(int b) {
        int lo = BUCKET_EDGES[b];
        int hiExclusive = BUCKET_EDGES[b + 1];
        return lo + "-" + (hiExclusive - 1);   // e.g. "60-74"
    }

    private static double rate(long joined, long shown) {
        return shown == 0 ? 0.0 : (double) joined / shown;
    }
}
