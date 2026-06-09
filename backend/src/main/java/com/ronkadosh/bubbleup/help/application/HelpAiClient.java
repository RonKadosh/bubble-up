package com.ronkadosh.bubbleup.help.application;

import java.util.List;
import java.util.Optional;

public interface HelpAiClient {
    Optional<String> answer(String question, String locale, String currentPath, List<HelpTopic> topics);
}
