package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.UserProfile;

public final class MatchingScorer {

    private MatchingScorer() {}

    public static double finalScore(int r, UserProfile profile, int daysActive, MatchingProperties props) {
        double qw = MatchingWeights.quizWeight(daysActive, props);
        return qw * profile.getQuizScore(r) + (1 - qw) * profile.getBehaviorScore(r);
    }

    public static double[] userFinalScores(UserProfile profile, int daysActive, MatchingProperties props) {
        double[] scores = new double[7];
        for (int r = 0; r < 7; r++) {
            scores[r] = finalScore(r, profile, daysActive, props);
        }
        return scores;
    }

    public static int matchPercent(double[] userFinal, GroupProfile group,
                                   double reliability, MatchingProperties props) {
        double[] targets = props.targets().asArray();
        double totalNeed = 0;
        double weighted = 0;
        for (int r = 0; r < 7; r++) {
            double need = Math.max(0, targets[r] - group.getAvgScore(r));
            totalNeed += need;
            weighted += need * userFinal[r];
        }
        double raw = totalNeed == 0 ? 0.5 : weighted / totalNeed;
        double pulled = 0.5 + (raw - 0.5) * reliability;
        return (int) Math.round(pulled * 100);
    }
}
