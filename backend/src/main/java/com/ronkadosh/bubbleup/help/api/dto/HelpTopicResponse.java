package com.ronkadosh.bubbleup.help.api.dto;

import com.ronkadosh.bubbleup.help.application.HelpTopic;

import java.util.List;

public record HelpTopicResponse(
        String id,
        String category,
        String audience,
        String title,
        String summary,
        List<String> steps,
        List<HelpActionResponse> actions,
        List<String> tags
) {
    public static HelpTopicResponse from(HelpTopic topic) {
        return new HelpTopicResponse(
                topic.id(),
                topic.category(),
                topic.audience().name(),
                topic.title(),
                topic.summary(),
                topic.steps(),
                topic.actions().stream()
                        .map(a -> new HelpActionResponse(a.label(), a.route()))
                        .toList(),
                topic.tags()
        );
    }
}
