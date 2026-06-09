package com.ronkadosh.bubbleup.help.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "help_question_entries",
        indexes = {
                @Index(name = "idx_help_questions_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_help_questions_normalized", columnList = "normalized_question,locale"),
                @Index(name = "idx_help_questions_cache", columnList = "normalized_question,locale,source,cacheable")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelpQuestionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String locale;

    @Column(name = "page_path", length = 200)
    private String currentPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "normalized_question", nullable = false, length = 320)
    private String normalizedQuestion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "matched_topic_ids", length = 500)
    private String matchedTopicIds;

    @Column(nullable = false)
    private boolean cacheable;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
