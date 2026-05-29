package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;

public final class MatchingWeights {

    private MatchingWeights() {}

    public static double quizWeight(int daysActive, MatchingProperties props) {
        if (daysActive <= props.weightShiftDayStart()) return props.quizWeightMax();
        if (daysActive >= props.weightShiftDayEnd())   return props.quizWeightMin();
        double range = props.weightShiftDayEnd() - props.weightShiftDayStart();
        double delta = props.quizWeightMax() - props.quizWeightMin();
        return props.quizWeightMax() - delta * (daysActive - props.weightShiftDayStart()) / range;
    }
}
