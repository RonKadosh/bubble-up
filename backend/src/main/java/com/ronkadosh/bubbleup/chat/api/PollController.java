package com.ronkadosh.bubbleup.chat.api;

import com.ronkadosh.bubbleup.chat.api.dto.CreatePollRequest;
import com.ronkadosh.bubbleup.chat.api.dto.PollResponse;
import com.ronkadosh.bubbleup.chat.api.dto.VotePollRequest;
import com.ronkadosh.bubbleup.chat.application.PollCommandService;
import com.ronkadosh.bubbleup.chat.application.PollQueryService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.CHAT_BASE)
@RequiredArgsConstructor
public class PollController {

    private final PollCommandService commands;
    private final PollQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/rooms/{roomId}/polls")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PollResponse> createPoll(
            @PathVariable UUID roomId,
            @Valid @RequestBody CreatePollRequest request) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.createPoll(roomId, me, request));
    }

    @GetMapping("/polls/{pollId}")
    public ApiResponse<PollResponse> getPoll(@PathVariable UUID pollId) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.getPoll(pollId, me));
    }

    @PostMapping("/polls/{pollId}/vote")
    public ApiResponse<PollResponse> vote(
            @PathVariable UUID pollId,
            @Valid @RequestBody VotePollRequest request) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.vote(pollId, me, request.optionIds()));
    }

    @PostMapping("/polls/{pollId}/close")
    public ApiResponse<PollResponse> closePoll(@PathVariable UUID pollId) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.closePoll(pollId, me));
    }
}
