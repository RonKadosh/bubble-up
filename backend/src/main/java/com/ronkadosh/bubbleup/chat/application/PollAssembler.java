package com.ronkadosh.bubbleup.chat.application;

import com.ronkadosh.bubbleup.chat.api.dto.PollOptionResponse;
import com.ronkadosh.bubbleup.chat.api.dto.PollResponse;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatPoll;
import com.ronkadosh.bubbleup.chat.model.ChatPollOption;
import com.ronkadosh.bubbleup.chat.model.ChatPollVote;
import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Package-private helpers shared by {@link PollCommandService} and {@link PollQueryService}.
 * No Spring wiring — pure static methods. Repositories are passed by the caller.
 */
final class PollAssembler {

    private PollAssembler() {}

    /**
     * Loads the room and asserts the caller is allowed in it (handles both group
     * and expert-session rooms). Throws {@link ErrorCode#CHAT_ROOM_NOT_FOUND},
     * {@link ErrorCode#NOT_GROUP_MEMBER}, or {@link ErrorCode#FORBIDDEN} on failure.
     */
    static ChatRoom requireRoomMember(
            ChatRoomRepository chatRoomRepository,
            ChatInternalService chatInternalService,
            UUID roomId,
            UUID userId
    ) {
        chatInternalService.assertRoomAccess(roomId, userId);
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    /**
     * Builds the {@link PollResponse} payload from a poll, its ordered options, and its votes.
     * Pure mapper — no DB access.
     */
    static PollResponse assemble(
            ChatPoll poll,
            List<ChatPollOption> options,
            Collection<ChatPollVote> votes,
            UUID userId
    ) {
        Map<UUID, List<UUID>> votersByOption = new HashMap<>();
        List<UUID> myVote = new ArrayList<>();
        for (ChatPollVote v : votes) {
            votersByOption.computeIfAbsent(v.getOptionId(), k -> new ArrayList<>()).add(v.getUserId());
            if (v.getUserId().equals(userId)) myVote.add(v.getOptionId());
        }
        List<PollOptionResponse> optionResponses = new ArrayList<>(options.size());
        for (ChatPollOption o : options) {
            List<UUID> v = votersByOption.getOrDefault(o.getId(), List.of());
            optionResponses.add(new PollOptionResponse(o.getId(), o.getText(), o.getPosition(), v.size(), v));
        }
        return new PollResponse(
                poll.getId(),
                poll.getRoomId(),
                poll.getMessageId(),
                poll.getQuestion(),
                poll.isAllowMultiple(),
                poll.getClosedAt(),
                poll.getCreatedByUserId(),
                poll.getCreatedAt(),
                optionResponses,
                votes.size(),
                myVote
        );
    }
}
