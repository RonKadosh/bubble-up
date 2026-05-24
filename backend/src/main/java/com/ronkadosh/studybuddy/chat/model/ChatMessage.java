package com.ronkadosh.studybuddy.chat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        indexes = @Index(name = "idx_chat_messages_room_sent", columnList = "room_id, sent_at DESC")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    /** Null for SYSTEM_* messages. */
    @Column(name = "sender_id")
    private UUID senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private ChatMessageType messageType;

    /** SYSTEM_JOIN/SYSTEM_LEAVE: the user who joined/left. Null otherwise. */
    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    /** LINK: target type. Null otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(name = "link_target_type", length = 32)
    private ChatLinkTargetType linkTargetType;

    /** LINK: target id. Null otherwise. */
    @Column(name = "link_target_id")
    private UUID linkTargetId;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) sentAt = Instant.now();
        if (messageType == null) messageType = ChatMessageType.TEXT;
    }
}
