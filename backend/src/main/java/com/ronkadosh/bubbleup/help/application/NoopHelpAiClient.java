package com.ronkadosh.bubbleup.help.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class NoopHelpAiClient implements HelpAiClient {

    @Override
    public Optional<String> answer(String question, String locale, String currentPath, List<HelpTopic> topics) {
        return Optional.empty();
    }
}
