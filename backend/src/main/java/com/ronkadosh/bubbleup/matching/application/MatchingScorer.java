package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.MatchResultType;
import com.ronkadosh.bubbleup.matching.model.UserProfile;

import java.util.List;

/**
 * Pure scoring primitives for the v1 matching engine. No I/O, no Spring — every
 * function is a deterministic transform of vectors + {@link MatchingProperties},
 * which keeps it unit-testable and the formula in one place.
 *
 * <p>Model (7 roles, indices 0..6 = Leader, Planner, Expert, Creative,
 * Communicator, TeamPlayer, Challenger):
 * <pre>
 *   member vector v_i  = userFinalScores(profile_i)            // quiz/behavior blend
 *   group_avg          = Σ(v_i · conf_i) / Σ(conf_i)           // confidence-weighted (in the service)
 *   need               = 1 − group_avg
 *   personalized_match = cosine(v0, need)                       // complementarity, not similarity
 *   user_confidence    = min(qW·min(answered/qCap,1) + bW·min(events/bCap,1), userCap)
 *   group_confidence   = avg(member_confs) · min(member_count / groupMemberCap, 1)
 *   matching_confidence= user_confidence · group_confidence
 *   trending_score     = Σ weight_k · min(signal_k / cap_k, 1)
 *   final_score        = matching_confidence·personalized_match + (1−matching_confidence)·trending_score
 * </pre>
 */
public final class MatchingScorer {

    private static final int ROLES = 7;

    private MatchingScorer() {}

    // ── Profile building (character vector from quiz answers) ────────────────

    /**
     * Builds the user's quiz character vector from their answered options. The
     * accumulated weights are <b>max-normalized</b> (divided by the strongest
     * role) rather than raw-summed, so the result is a count-invariant <i>shape</i>:
     * answering the same way 3× or 30× yields the same vector, and a dominant role
     * stays at 1.0 instead of being flattened by a clamp as secondary roles catch
     * up. "How much we've learned" is carried separately by user_confidence, not
     * by the vector's magnitude. An empty answer set returns all-zeros (cold user).
     */
    public static double[] buildQuizVector(List<double[]> answerWeights) {
        double[] sum = new double[ROLES];
        for (double[] w : answerWeights) {
            for (int r = 0; r < ROLES; r++) sum[r] += w[r];
        }
        return maxNormalize(sum);
    }

    /** Scales {@code v} so its largest component is 1.0 (shape only). All-zero (or non-positive) → unchanged. */
    public static double[] maxNormalize(double[] v) {
        double max = 0.0;
        for (double x : v) max = Math.max(max, x);
        if (max <= 0.0) return v.clone();
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / max;
        return out;
    }

    // ── Member / group vector construction ───────────────────────────────────

    public static double finalScore(int r, UserProfile profile, int daysActive, MatchingProperties props) {
        double qw = MatchingWeights.quizWeight(daysActive, props);
        return qw * profile.getQuizScore(r) + (1 - qw) * profile.getBehaviorScore(r);
    }

    public static double[] userFinalScores(UserProfile profile, int daysActive, MatchingProperties props) {
        double[] scores = new double[ROLES];
        for (int r = 0; r < ROLES; r++) {
            scores[r] = finalScore(r, profile, daysActive, props);
        }
        return scores;
    }

    // ── Complementarity match ────────────────────────────────────────────────

    /** {@code need[r] = max(0, 1 − group_avg[r])} — the roles the group is short on. */
    public static double[] groupNeed(GroupProfile group) {
        double[] need = new double[ROLES];
        for (int r = 0; r < ROLES; r++) {
            need[r] = Math.max(0.0, 1.0 - group.getAvgScore(r));
        }
        return need;
    }

    /**
     * Cosine similarity between the user vector and the group's need vector — high
     * when the user is strong exactly where the group is weak. Degenerate inputs
     * (a zero user vector, or a group already maxed on every role so need is zero)
     * return the neutral 0.5 rather than an undefined 0/0.
     */
    public static double cosineComplementarity(double[] v0, double[] need) {
        double dot = 0, n0 = 0, n1 = 0;
        for (int r = 0; r < ROLES; r++) {
            dot += v0[r] * need[r];
            n0 += v0[r] * v0[r];
            n1 += need[r] * need[r];
        }
        if (n0 == 0.0 || n1 == 0.0) return 0.5;
        return dot / (Math.sqrt(n0) * Math.sqrt(n1));
    }

    // ── Confidence ───────────────────────────────────────────────────────────

    public static double userConfidence(int answeredQuestions, int behaviorEvents, MatchingProperties props) {
        MatchingProperties.Confidence c = props.confidence();
        double qc = Math.min(answeredQuestions / c.questionCap(), 1.0);
        double bc = Math.min(behaviorEvents / c.behaviorCap(), 1.0);
        return Math.min(c.questionWeight() * qc + c.behaviorWeight() * bc, c.userCap());
    }

    /**
     * How much the group's characteristic profile can be trusted: the average of
     * its members' confidences, damped by a size factor so a 1-member group never
     * reads as fully reliable. {@code memberConfs} holds one entry per member that
     * has a profile; {@code memberCount} is the true member count.
     */
    public static double groupConfidence(double[] memberConfs, int memberCount, MatchingProperties props) {
        if (memberConfs.length == 0) return 0.0;
        double sum = 0;
        for (double c : memberConfs) sum += c;
        double avg = sum / memberConfs.length;
        double sizeFactor = Math.min(memberCount / props.confidence().groupMemberCap(), 1.0);
        return avg * sizeFactor;
    }

    // ── Trending (cold-start popularity) ─────────────────────────────────────

    public static double trendingScore(long activityCount, int recentJoins, int memberCount,
                                       int upcomingSessions, MatchingProperties props) {
        MatchingProperties.Trending t = props.trending();
        return t.activityWeight()    * norm(activityCount,   t.activityCap())
             + t.recentJoinWeight()  * norm(recentJoins,     t.recentJoinCap())
             + t.memberCountWeight() * norm(memberCount,     t.memberCountCap())
             + t.upcomingWeight()    * norm(upcomingSessions, t.upcomingCap());
    }

    private static double norm(double raw, double cap) {
        if (cap <= 0) return 0.0;
        return Math.min(raw / cap, 1.0);
    }

    // ── Blend ────────────────────────────────────────────────────────────────

    public static double blend(double matchingConfidence, double personalizedMatch, double trendingScore) {
        return matchingConfidence * personalizedMatch + (1 - matchingConfidence) * trendingScore;
    }

    /**
     * The full per-candidate scoring for one (user, group) pair. Centralized so the
     * live path ({@code MatchingQueryService.computeLive}) and the cache-build path
     * ({@code MatchingCommandService.refreshUserMatchCache}) cannot drift apart.
     *
     * @param userFinal      the user's role vector (already blended for daysActive)
     * @param userConfidence the user's confidence (independent of the group)
     */
    public static Scored score(double[] userFinal, double userConfidence,
                               GroupProfile group, MatchingProperties props) {
        double matchingConfidence = userConfidence * group.getGroupProfileConfidence();
        double personalized = cosineComplementarity(userFinal, groupNeed(group));
        double trending = trendingScore(
                group.getTrendingActivityCount(), group.getTrendingRecentJoins(),
                group.getMemberCount(), group.getTrendingUpcomingSessions(), props);
        double finalScore = blend(matchingConfidence, personalized, trending);
        MatchResultType mode = matchingConfidence >= props.matchedDisplayThreshold()
                ? MatchResultType.MATCHED : MatchResultType.TRENDING;
        Integer matchPercent = mode == MatchResultType.MATCHED ? (int) Math.round(personalized * 100) : null;
        return new Scored(finalScore, matchingConfidence, mode, matchPercent);
    }

    /** Result of {@link #score}: the ordering key plus the display fields. */
    public record Scored(double finalScore, double matchingConfidence,
                         MatchResultType mode, Integer matchPercent) {}
}
