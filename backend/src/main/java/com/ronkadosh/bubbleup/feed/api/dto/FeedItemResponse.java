package com.ronkadosh.bubbleup.feed.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One item in the dashboard feed. A flat tagged record — {@code kind} tells the
 * frontend renderer registry which branch to use; the per-kind fields below are
 * populated only where relevant (null fields are dropped from the wire).
 *
 * <p>{@code ts} is the universal sort key: occurrence time for upcoming/live items,
 * activity time for messages/files/membership. Null for discovery (insertion order).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedItemResponse(
        String kind,
        UUID groupId,
        String groupName,
        Instant ts,
        String title,
        String subtitle,
        // per-kind extras
        Instant startsAt,
        Instant endsAt,
        String eventType,
        Integer unreadCount,
        Integer matchPercent,
        Integer memberCount,
        Integer participantCount,
        // discovery (recommendation) extras: MATCHED → matchPercent; TRENDING → reasonLabels.
        // courseCode/courseName tell the user which course a recommended Bubble belongs to.
        String displayMode,
        List<String> reasonLabels,
        String courseCode,
        String courseName,
        // collapse extras (membership / file rollups): the lead labels (actor names or
        // file names, capped ~3) plus the total distinct count, so the frontend can render
        // "<labels> and N others/more". collapsedCount == labels.size() → no overflow.
        List<String> collapsedLabels,
        Integer collapsedCount,
        FeedCta cta
) {
    /** Builder-ish factory keeps source call sites readable despite the wide record. */
    public static Builder of(String kind) {
        return new Builder(kind);
    }

    public static final class Builder {
        private final String kind;
        private UUID groupId;
        private String groupName;
        private Instant ts;
        private String title;
        private String subtitle;
        private Instant startsAt;
        private Instant endsAt;
        private String eventType;
        private Integer unreadCount;
        private Integer matchPercent;
        private Integer memberCount;
        private Integer participantCount;
        private String displayMode;
        private List<String> reasonLabels;
        private String courseCode;
        private String courseName;
        private List<String> collapsedLabels;
        private Integer collapsedCount;
        private FeedCta cta;

        private Builder(String kind) { this.kind = kind; }

        public Builder group(UUID id, String name) { this.groupId = id; this.groupName = name; return this; }
        public Builder ts(Instant ts) { this.ts = ts; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder subtitle(String subtitle) { this.subtitle = subtitle; return this; }
        public Builder startsAt(Instant startsAt) { this.startsAt = startsAt; return this; }
        public Builder endsAt(Instant endsAt) { this.endsAt = endsAt; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder unreadCount(Integer n) { this.unreadCount = n; return this; }
        public Builder matchPercent(Integer n) { this.matchPercent = n; return this; }
        public Builder memberCount(Integer n) { this.memberCount = n; return this; }
        public Builder participantCount(Integer n) { this.participantCount = n; return this; }
        public Builder displayMode(String m) { this.displayMode = m; return this; }
        public Builder reasonLabels(List<String> labels) { this.reasonLabels = labels; return this; }
        public Builder course(String code, String name) { this.courseCode = code; this.courseName = name; return this; }
        public Builder collapsed(List<String> labels, int count) {
            this.collapsedLabels = labels; this.collapsedCount = count; return this;
        }
        public Builder cta(String type, UUID targetId) {
            this.cta = new FeedCta(type, targetId == null ? null : targetId.toString());
            return this;
        }

        public FeedItemResponse build() {
            return new FeedItemResponse(kind, groupId, groupName, ts, title, subtitle,
                    startsAt, endsAt, eventType, unreadCount, matchPercent, memberCount,
                    participantCount, displayMode, reasonLabels, courseCode, courseName,
                    collapsedLabels, collapsedCount, cta);
        }
    }
}
