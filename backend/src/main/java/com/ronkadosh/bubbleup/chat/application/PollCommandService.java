package com.ronkadosh.bubbleup.chat.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.bubbleup.chat.api.dto.CreatePollRequest;
import com.ronkadosh.bubbleup.chat.api.dto.PollResponse;
import com.ronkadosh.bubbleup.chat.api.dto.PollUpdateEvent;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.model.ChatPoll;
import com.ronkadosh.bubbleup.chat.model.ChatPollOption;
import com.ronkadosh.bubbleup.chat.model.ChatPollVote;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatPollOptionRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatPollRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatPollVoteRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Write side of polls. Creating a poll posts a LINK chat message that points back at the
 * poll. Votes and closes both broadcast a {@link PollUpdateEvent} on
 * {@code /topic/chat/{roomId}/polls}; the original LINK message rides
 * {@code /topic/chat/{roomId}}.
 */
@Service
@RequiredArgsConstructor
public class PollCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatPollRepository chatPollRepository;
    private final ChatPollOptionRepository chatPollOptionRepository;
    private final ChatPollVoteRepository chatPollVoteRepository;
    private final ChatInternalService chatInternalService;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;
    private final AuthInternalService authInternalService;

    @Transactional
    public PollResponse createPoll(UUID roomId, UUID userId, CreatePollRequest request) {
        PollAssembler.requireRoomMember(chatRoomRepository, chatInternalService, roomId, userId);

        Set<String> seen = new HashSet<>();
        for (String opt : request.options()) {
            if (opt == null || opt.isBlank()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "poll options must be non-blank");
            }
            if (!seen.add(opt.trim())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "duplicate poll option: " + opt);
            }
        }

        Instant now = timeProvider.now();
        boolean allowMultiple = request.allowMultiple() == null ? true : request.allowMultiple();

        // First create the LINK message — we need its id to wire to the poll.
        ChatMessage linkMessage = chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(userId)
                .content(request.question())
                .messageType(ChatMessageType.LINK)
                .linkTargetType(ChatLinkTargetType.POLL)
                // linkTargetId is the poll id, filled in below once we have it
                .sentAt(now)
                .build());

        ChatPoll poll = chatPollRepository.save(ChatPoll.builder()
                .roomId(roomId)
                .messageId(linkMessage.getId())
                .question(request.question())
                .allowMultiple(allowMultiple)
                .createdByUserId(userId)
                .createdAt(now)
                .build());

        // Now that the poll exists, point the LINK message at it.
        linkMessage = ChatMessage.builder()
                .id(linkMessage.getId())
                .roomId(linkMessage.getRoomId())
                .senderId(linkMessage.getSenderId())
                .content(linkMessage.getContent())
                .messageType(linkMessage.getMessageType())
                .linkTargetType(linkMessage.getLinkTargetType())
                .linkTargetId(poll.getId())
                .sentAt(linkMessage.getSentAt())
                .build();
        chatMessageRepository.save(linkMessage);

        List<ChatPollOption> savedOptions = new ArrayList<>();
        int pos = 0;
        for (String text : request.options()) {
            savedOptions.add(chatPollOptionRepository.save(ChatPollOption.builder()
                    .pollId(poll.getId())
                    .text(text.trim())
                    .position(pos++)
                    .build()));
        }

        // Broadcast the LINK message so subscribers render the inline poll card.
        UserIdentity senderIdentity = authInternalService.getIdentity(userId).orElse(null);
        webSocketPublisher.publishToTopic(
                WebSocketDestination.chatRoom(roomId),
                ChatMessageResponse.from(linkMessage, senderIdentity)
        );

        return PollAssembler.assemble(poll, savedOptions, List.of(), userId);
    }

    @Transactional
    public PollResponse vote(UUID pollId, UUID userId, List<UUID> optionIds) {
        ChatPoll poll = chatPollRepository.findById(pollId)
                .orElseThrow(() -> new AppException(ErrorCode.POLL_NOT_FOUND));
        PollAssembler.requireRoomMember(chatRoomRepository, chatInternalService, poll.getRoomId(), userId);
        if (poll.getClosedAt() != null) {
            throw new AppException(ErrorCode.POLL_CLOSED);
        }
        if (optionIds == null || optionIds.isEmpty()) {
            throw new AppException(ErrorCode.POLL_INVALID_VOTE_COUNT);
        }
        if (!poll.isAllowMultiple() && optionIds.size() != 1) {
            throw new AppException(ErrorCode.POLL_INVALID_VOTE_COUNT,
                    "single-choice poll requires exactly one optionId");
        }

        List<ChatPollOption> options = chatPollOptionRepository.findAllByPollIdOrderByPositionAsc(pollId);
        Set<UUID> validOptionIds = new HashSet<>();
        for (ChatPollOption o : options) validOptionIds.add(o.getId());

        Set<UUID> requested = new HashSet<>(optionIds);
        for (UUID requestedId : requested) {
            if (!validOptionIds.contains(requestedId)) {
                throw new AppException(ErrorCode.POLL_OPTION_INVALID,
                        "option " + requestedId + " does not belong to poll " + pollId);
            }
        }

        // Diff existing votes vs requested set: remove unwanted, add missing.
        List<ChatPollVote> existing = chatPollVoteRepository.findAllByPollIdAndUserId(pollId, userId);
        Set<UUID> existingOptionIds = new HashSet<>();
        for (ChatPollVote v : existing) existingOptionIds.add(v.getOptionId());

        Set<UUID> toRemove = new HashSet<>(existingOptionIds);
        toRemove.removeAll(requested);
        if (!toRemove.isEmpty()) {
            chatPollVoteRepository.deleteByPollIdAndUserIdAndOptionIdIn(pollId, userId, toRemove);
        }

        Set<UUID> toAdd = new HashSet<>(requested);
        toAdd.removeAll(existingOptionIds);
        Instant now = timeProvider.now();
        for (UUID optId : toAdd) {
            chatPollVoteRepository.save(ChatPollVote.builder()
                    .pollId(pollId)
                    .optionId(optId)
                    .userId(userId)
                    .votedAt(now)
                    .build());
        }

        List<ChatPollVote> refreshed = chatPollVoteRepository.findAllByPollId(pollId);
        broadcastPollUpdate(poll, options, refreshed);
        return PollAssembler.assemble(poll, options, refreshed, userId);
    }

    @Transactional
    public PollResponse closePoll(UUID pollId, UUID userId) {
        ChatPoll poll = chatPollRepository.findById(pollId)
                .orElseThrow(() -> new AppException(ErrorCode.POLL_NOT_FOUND));
        PollAssembler.requireRoomMember(chatRoomRepository, chatInternalService, poll.getRoomId(), userId);
        if (!poll.getCreatedByUserId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_POLL_OWNER);
        }
        if (poll.getClosedAt() == null) {
            poll.setClosedAt(timeProvider.now());
            chatPollRepository.save(poll);
        }
        List<ChatPollOption> options = chatPollOptionRepository.findAllByPollIdOrderByPositionAsc(pollId);
        List<ChatPollVote> votes = chatPollVoteRepository.findAllByPollId(pollId);
        broadcastPollUpdate(poll, options, votes);
        return PollAssembler.assemble(poll, options, votes, userId);
    }

    private void broadcastPollUpdate(ChatPoll poll, List<ChatPollOption> options, List<ChatPollVote> votes) {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        Map<UUID, List<UUID>> voters = new LinkedHashMap<>();
        for (ChatPollOption o : options) {
            counts.put(o.getId(), 0);
            voters.put(o.getId(), new ArrayList<>());
        }
        for (ChatPollVote v : votes) {
            counts.merge(v.getOptionId(), 1, Integer::sum);
            voters.computeIfAbsent(v.getOptionId(), k -> new ArrayList<>()).add(v.getUserId());
        }
        PollUpdateEvent event = new PollUpdateEvent(
                poll.getId(), counts, voters, votes.size(), poll.getClosedAt());
        webSocketPublisher.publishToTopic(WebSocketDestination.chatRoomPolls(poll.getRoomId()), event);
    }
}
