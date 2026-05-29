package com.ronkadosh.bubbleup.chat.application;

import com.ronkadosh.bubbleup.chat.api.dto.PollResponse;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatPoll;
import com.ronkadosh.bubbleup.chat.model.ChatPollOption;
import com.ronkadosh.bubbleup.chat.model.ChatPollVote;
import com.ronkadosh.bubbleup.chat.persistence.ChatPollOptionRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatPollRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatPollVoteRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of polls. Writes (create / vote / close) live on {@link PollCommandService}.
 */
@Service
@RequiredArgsConstructor
public class PollQueryService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatPollRepository chatPollRepository;
    private final ChatPollOptionRepository chatPollOptionRepository;
    private final ChatPollVoteRepository chatPollVoteRepository;
    private final ChatInternalService chatInternalService;

    @Transactional(readOnly = true)
    public PollResponse getPoll(UUID pollId, UUID userId) {
        ChatPoll poll = chatPollRepository.findById(pollId)
                .orElseThrow(() -> new AppException(ErrorCode.POLL_NOT_FOUND));
        PollAssembler.requireRoomMember(chatRoomRepository, chatInternalService, poll.getRoomId(), userId);
        List<ChatPollOption> options = chatPollOptionRepository.findAllByPollIdOrderByPositionAsc(pollId);
        List<ChatPollVote> votes = chatPollVoteRepository.findAllByPollId(pollId);
        return PollAssembler.assemble(poll, options, votes, userId);
    }
}
