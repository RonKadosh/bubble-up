package com.ronkadosh.bubbleup.help.application;

import com.ronkadosh.bubbleup.common.config.HelpAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.help.ai", name = "provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiHelpClient implements HelpAiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final HelpAiProperties properties;

    public OpenAiHelpClient(HelpAiProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> answer(String question, String locale, String currentPath, List<HelpTopic> topics) {
        if (!properties.configured() || topics.isEmpty()) {
            return Optional.empty();
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties.timeout() != null) {
            requestFactory.setConnectTimeout(properties.timeout());
            requestFactory.setReadTimeout(properties.timeout());
        }

        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        try {
            Map<String, Object> response = client.post()
                    .uri("/responses")
                    .body(requestBody(question, locale, currentPath, topics))
                    .retrieve()
                    .body(MAP_TYPE);
            return contentFrom(response);
        } catch (RestClientException | ClassCastException ex) {
            log.warn("Help AI request failed; falling back to local help catalog", ex);
            return Optional.empty();
        }
    }

    private Map<String, Object> requestBody(String question, String locale, String currentPath, List<HelpTopic> topics) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_output_tokens", 600);
        body.put("reasoning", Map.of("effort", "minimal"));
        body.put("text", Map.of("verbosity", "low"));
        body.put("instructions", """
                You are Bubble.up Help Assistant.
                Answer only from the provided safe help topics.
                Do not mention backend code, security internals, tokens, databases, infrastructure, admin moderation internals, audit logs, or implementation details.
                If the answer is not covered, say you are not sure and suggest opening Help or contacting support.
                Keep answers short, warm, and action-oriented.
                """);
        body.put("input", userPrompt(question, locale, currentPath, topics));
        return body;
    }

    private String userPrompt(String question, String locale, String currentPath, List<HelpTopic> topics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Locale: ").append(locale == null || locale.isBlank() ? "en" : locale).append('\n');
        sb.append("Current path: ").append(currentPath == null ? "" : currentPath).append('\n');
        sb.append("Question: ").append(question).append("\n\n");
        sb.append("Safe help topics:\n");
        for (HelpTopic topic : topics) {
            sb.append("- ").append(topic.title()).append(" (").append(topic.id()).append(")\n");
            sb.append("  Summary: ").append(topic.summary()).append('\n');
            sb.append("  Steps: ").append(String.join(" | ", topic.steps())).append('\n');
            sb.append("  Actions: ");
            List<String> actions = new ArrayList<>();
            for (HelpAction action : topic.actions()) {
                actions.add(action.label() + " -> " + action.route());
            }
            sb.append(String.join(", ", actions)).append("\n\n");
        }
        return sb.toString();
    }

    private Optional<String> contentFrom(Map<String, Object> response) {
        if (response == null) {
            return Optional.empty();
        }
        Object outputText = response.get("output_text");
        if (outputText instanceof String text && !text.isBlank()) {
            return Optional.of(text.trim());
        }

        Object outputObj = response.get("output");
        if (!(outputObj instanceof List<?> output)) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        for (Object itemObj : output) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            Object contentObj = item.get("content");
            if (!(contentObj instanceof List<?> content)) {
                continue;
            }
            for (Object contentItemObj : content) {
                if (!(contentItemObj instanceof Map<?, ?> contentItem)) {
                    continue;
                }
                Object partText = contentItem.get("text");
                if (partText instanceof String part && !part.isBlank()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(part.trim());
                }
            }
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
    }
}
