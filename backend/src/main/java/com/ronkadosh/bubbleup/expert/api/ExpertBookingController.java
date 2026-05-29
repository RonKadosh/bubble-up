package com.ronkadosh.bubbleup.expert.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.expert.api.dto.BookingRequestResponse;
import com.ronkadosh.bubbleup.expert.api.dto.CreateBookingRequest;
import com.ronkadosh.bubbleup.expert.application.ExpertBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.EXPERT_BOOKINGS_BASE)
@RequiredArgsConstructor
public class ExpertBookingController {

    private final ExpertBookingService bookings;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingRequestResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(bookings.createRequest(me.id(), request));
    }

    /**
     * Lists booking requests for the current user. {@code asExpert=true} returns
     * inbound requests addressed to me (the expert); {@code asExpert=false}
     * returns outbound requests I (group owner) sent.
     */
    @GetMapping("/mine")
    public ApiResponse<List<BookingRequestResponse>> listMine(
            @RequestParam(defaultValue = "false") boolean asExpert) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(asExpert
                ? bookings.listForExpert(me.id())
                : bookings.listForRequester(me.id()));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<BookingRequestResponse> accept(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(bookings.acceptRequest(id, me.id()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<BookingRequestResponse> reject(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(bookings.rejectRequest(id, me.id()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<BookingRequestResponse> withdraw(@PathVariable UUID id) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(bookings.withdrawRequest(id, me.id()));
    }
}
