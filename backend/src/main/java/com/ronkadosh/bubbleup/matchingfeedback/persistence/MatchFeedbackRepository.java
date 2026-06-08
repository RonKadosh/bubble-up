package com.ronkadosh.bubbleup.matchingfeedback.persistence;

import com.ronkadosh.bubbleup.matchingfeedback.model.FeedbackRating;
import com.ronkadosh.bubbleup.matchingfeedback.model.MatchFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchFeedbackRepository extends JpaRepository<MatchFeedback, UUID> {

    Optional<MatchFeedback> findByUserIdAndGroupId(UUID userId, UUID groupId);

    /** Shown vs joined counts per display mode (MATCHED / TRENDING) — the core conversion funnel. */
    @Query("""
            SELECT f.lastDisplayMode AS mode,
                   COUNT(f) AS shown,
                   SUM(CASE WHEN f.joinedAt IS NOT NULL THEN 1 ELSE 0 END) AS joined
            FROM MatchFeedback f
            WHERE f.lastDisplayMode IS NOT NULL
            GROUP BY f.lastDisplayMode
            """)
    List<ModeFunnelRow> funnelByMode();

    /** Overall explicit-rating tally (GOOD_FIT / BAD_FIT). */
    @Query("""
            SELECT f.rating AS rating, COUNT(f) AS count
            FROM MatchFeedback f
            WHERE f.rating IS NOT NULL
            GROUP BY f.rating
            """)
    List<RatingCountRow> ratingCounts();

    /**
     * Per-row projection for match-%-bucket analysis (join-rate and rating-by-bucket).
     * Only MATCHED rows carry a percent, so TRENDING is naturally excluded. Bucketing is
     * done in Java — volume is low and it keeps the SQL portable.
     */
    @Query("""
            SELECT f.lastMatchPercent AS matchPercent, f.joinedAt AS joinedAt, f.rating AS rating
            FROM MatchFeedback f
            WHERE f.lastMatchPercent IS NOT NULL
            """)
    List<PercentRow> shownWithPercent();

    interface ModeFunnelRow {
        String getMode();
        long getShown();
        long getJoined();
    }

    interface RatingCountRow {
        FeedbackRating getRating();
        long getCount();
    }

    interface PercentRow {
        Integer getMatchPercent();
        Instant getJoinedAt();
        FeedbackRating getRating();
    }
}
