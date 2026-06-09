package com.ronkadosh.bubbleup.help.application;

import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.help.api.dto.HelpActionResponse;
import com.ronkadosh.bubbleup.help.api.dto.HelpAskRequest;
import com.ronkadosh.bubbleup.help.api.dto.HelpAskResponse;
import com.ronkadosh.bubbleup.help.api.dto.HelpQuestionResponse;
import com.ronkadosh.bubbleup.help.api.dto.HelpTopicResponse;
import com.ronkadosh.bubbleup.help.model.HelpQuestionEntry;
import com.ronkadosh.bubbleup.help.persistence.HelpQuestionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HelpQueryService {

    private static final int MAX_TOPIC_RESULTS = 8;
    private static final int MAX_ASK_CONTEXT_TOPICS = 4;
    private static final int KNOWN_QUESTION_SCORE = 24;
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "about", "after", "again", "app", "before", "can", "could", "first", "from", "get", "how",
            "into", "lost", "open", "opening", "please", "should", "simple", "simplest", "start",
            "thing", "things", "this", "use", "useful", "using", "want", "what", "when", "where",
            "which", "with", "would", "אני", "אתה", "איך", "מה", "איפה", "מתי", "שלי", "צריך"
    );

    private final HelpCatalog catalog;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<HelpAiClient> aiClients;
    private final HelpQuestionEntryRepository questionEntries;

    public List<HelpTopicResponse> topics(String query, String currentPath) {
        List<HelpTopic> visible = visibleTopics(currentUserProvider.get());
        return rank(visible, query, currentPath, MAX_TOPIC_RESULTS).stream()
                .map(ScoredTopic::topic)
                .map(HelpTopicResponse::from)
                .toList();
    }

    public HelpAskResponse ask(HelpAskRequest request) {
        CurrentUser user = currentUserProvider.get();
        List<HelpTopic> visible = visibleTopics(user);
        String normalizedQuestion = normalize(request.question());
        String locale = locale(request.locale());
        List<ScoredTopic> scoredMatches = rank(visible, request.question(), request.currentPath(), MAX_ASK_CONTEXT_TOPICS);
        List<HelpTopic> matches = scoredMatches.stream().map(ScoredTopic::topic).toList();

        boolean knownQuestion = !scoredMatches.isEmpty() && scoredMatches.get(0).score() >= KNOWN_QUESTION_SCORE;
        List<HelpTopic> aiContext = matches.isEmpty()
                ? rank(visible, "", request.currentPath(), MAX_ASK_CONTEXT_TOPICS).stream().map(ScoredTopic::topic).toList()
                : matches;
        Optional<HelpQuestionEntry> cachedAnswer = knownQuestion
                ? Optional.empty()
                : cachedAiAnswer(normalizedQuestion, locale, request.question());
        Optional<String> aiAnswer = knownQuestion
                || cachedAnswer.isPresent()
                ? Optional.empty()
                : aiClients.orderedStream()
                        .map(client -> client.answer(request.question(), request.locale(), request.currentPath(), aiContext))
                        .flatMap(OptionalCompat::stream)
                        .findFirst();

        String answer = cachedAnswer.map(HelpQuestionEntry::getAnswer)
                .orElseGet(() -> aiAnswer.orElseGet(() -> localAnswer(request.question(), matches)));
        String source = cachedAnswer.isPresent() ? "CACHE" : aiAnswer.isPresent() ? "OPENAI" : "LOCAL";
        List<HelpTopicResponse> topics = matches.stream().map(HelpTopicResponse::from).toList();
        LinkedHashMap<String, HelpActionResponse> actionMap = new LinkedHashMap<>();
        for (HelpTopic topic : matches) {
            for (HelpAction action : topic.actions()) {
                actionMap.putIfAbsent(action.route(), new HelpActionResponse(action.label(), action.route()));
            }
        }
        List<HelpActionResponse> actions = List.copyOf(actionMap.values());
        saveQuestion(user, request, normalizedQuestion, locale, answer, source, matches);
        return new HelpAskResponse(answer, source, topics, actions);
    }

    public List<HelpQuestionResponse> recentQuestions(String query, int limit) {
        CurrentUser user = currentUserProvider.get();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        Pageable pageable = PageRequest.of(0, safeLimit);
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return questionEntries.findByUserIdOrderByCreatedAtDesc(user.id(), pageable)
                    .stream()
                    .map(HelpQuestionResponse::from)
                    .toList();
        }
        return questionEntries.searchUserEntries(user.id(), normalizedQuery, pageable)
                .stream()
                .map(HelpQuestionResponse::from)
                .toList();
    }

    private List<HelpTopic> visibleTopics(CurrentUser user) {
        return catalog.all().stream()
                .filter(topic -> topic.audience().visibleTo(user.role()))
                .toList();
    }

    private List<ScoredTopic> rank(List<HelpTopic> topics, String query, String currentPath, int limit) {
        String normalizedQuery = normalize(query);
        Set<String> terms = tokens(normalizedQuery);
        String path = currentPath == null ? "" : currentPath;

        return topics.stream()
                .map(topic -> new ScoredTopic(topic, score(topic, normalizedQuery, terms, path)))
                .sorted(Comparator.comparingInt(ScoredTopic::score).reversed()
                .thenComparing(st -> st.topic().title()))
                .filter(st -> st.score() > 0 || normalizedQuery.isBlank())
                .limit(limit)
                .toList();
    }

    private int score(HelpTopic topic, String query, Set<String> terms, String currentPath) {
        int score = 0;
        String haystack = normalize(String.join(" ",
                topic.title(),
                topic.summary(),
                String.join(" ", topic.steps()),
                String.join(" ", topic.tags()),
                String.join(" ", topic.keywords())
        ));
        Set<String> haystackTerms = tokens(haystack);

        if (!query.isBlank() && haystack.contains(query)) {
            score += 18;
        }
        for (String term : terms) {
            if (topic.title().toLowerCase(Locale.ROOT).contains(term)) {
                score += 8;
            }
            if (topic.tags().stream().map(this::normalize).anyMatch(tag -> tag.contains(term))) {
                score += 6;
            }
            if (topic.keywords().stream().map(this::normalize).anyMatch(keyword -> keyword.contains(term))) {
                score += 6;
            }
            if (haystackTerms.contains(term)) {
                score += 2;
            }
        }
        for (HelpAction action : topic.actions()) {
            if (!currentPath.isBlank() && currentPath.startsWith(action.route())) {
                score += 10;
            }
        }
        return score;
    }

    private String localAnswer(String question, List<HelpTopic> matches) {
        if (matches.isEmpty()) {
            return "I am not sure from the current help guide. Try opening Help topics, or contact support if you are stuck.";
        }
        HelpTopic best = matches.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(best.summary()).append('\n');
        for (int i = 0; i < Math.min(4, best.steps().size()); i++) {
            sb.append(i + 1).append(". ").append(best.steps().get(i)).append('\n');
        }
        if (!best.actions().isEmpty()) {
            sb.append("Start here: ").append(best.actions().get(0).label()).append(".");
        }
        return sb.toString().trim();
    }

    private Optional<HelpQuestionEntry> cachedAiAnswer(String normalizedQuestion, String locale, String question) {
        if (!cacheableQuestion(question, normalizedQuestion)) {
            return Optional.empty();
        }
        return questionEntries.findFirstByNormalizedQuestionAndLocaleAndSourceAndCacheableTrueOrderByCreatedAtDesc(
                normalizedQuestion,
                locale,
                "OPENAI"
        );
    }

    private void saveQuestion(
            CurrentUser user,
            HelpAskRequest request,
            String normalizedQuestion,
            String locale,
            String answer,
            String source,
            List<HelpTopic> matches
    ) {
        if (normalizedQuestion.isBlank()) {
            return;
        }
        questionEntries.save(HelpQuestionEntry.builder()
                .userId(user.id())
                .locale(locale)
                .currentPath(truncate(request.currentPath(), 200))
                .question(truncate(request.question(), 500))
                .normalizedQuestion(truncate(normalizedQuestion, 320))
                .answer(truncate(answer, 4_000))
                .source(source)
                .matchedTopicIds(truncate(topicIds(matches), 500))
                .cacheable("OPENAI".equals(source) && cacheableQuestion(request.question(), normalizedQuestion))
                .build());
    }

    private String locale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        return truncate(normalize(locale), 16);
    }

    private boolean cacheableQuestion(String question, String normalizedQuestion) {
        if (question == null || normalizedQuestion.isBlank() || question.length() > 180) {
            return false;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        return !lower.contains("@") && !lower.matches(".*\\d{4,}.*");
    }

    private String topicIds(List<HelpTopic> matches) {
        List<String> ids = new ArrayList<>();
        for (HelpTopic topic : matches) {
            ids.add(topic.id());
        }
        return String.join(",", ids);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return NON_WORD.matcher(normalized).replaceAll(" ").trim();
    }

    private Set<String> tokens(String input) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : input.split("\\s+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private record ScoredTopic(HelpTopic topic, int score) {}

    private static final class OptionalCompat {
        private OptionalCompat() {}

        static <T> java.util.stream.Stream<T> stream(java.util.Optional<T> optional) {
            return optional.map(java.util.stream.Stream::of).orElseGet(java.util.stream.Stream::empty);
        }
    }
}
