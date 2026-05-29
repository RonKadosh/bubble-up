package com.ronkadosh.bubbleup.chat.persistence;

import com.ronkadosh.bubbleup.chat.model.ChatPollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatPollVoteRepository extends JpaRepository<ChatPollVote, UUID> {

    List<ChatPollVote> findAllByPollId(UUID pollId);

    List<ChatPollVote> findAllByPollIdAndUserId(UUID pollId, UUID userId);

    @Modifying
    @Query("delete from ChatPollVote v where v.pollId = :pollId and v.userId = :userId and v.optionId in :optionIds")
    int deleteByPollIdAndUserIdAndOptionIdIn(
            @Param("pollId") UUID pollId,
            @Param("userId") UUID userId,
            @Param("optionIds") Collection<UUID> optionIds);
}
