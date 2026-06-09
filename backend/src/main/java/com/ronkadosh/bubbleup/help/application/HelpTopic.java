package com.ronkadosh.bubbleup.help.application;

import java.util.List;

public record HelpTopic(
        String id,
        String category,
        HelpAudience audience,
        String title,
        String summary,
        List<String> steps,
        List<HelpAction> actions,
        List<String> tags,
        List<String> keywords
) {}
