package com.ronkadosh.bubbleup.help.api.dto;

import com.ronkadosh.bubbleup.help.model.HelpQuestionEntry;

import java.time.Instant;
import java.util.UUID;

public record HelpQuestionResponse(
        UUID id,
        String question,
        String answer,
        String source,
        String locale,
        String currentPath,
        Instant createdAt
) {
    public static HelpQuestionResponse from(HelpQuestionEntry entry) {
        return new HelpQuestionResponse(
                entry.getId(),
                entry.getQuestion(),
                entry.getAnswer(),
                entry.getSource(),
                entry.getLocale(),
                entry.getCurrentPath(),
                entry.getCreatedAt()
        );
    }
}
