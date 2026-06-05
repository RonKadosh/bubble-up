package com.ronkadosh.bubbleup.calendar.api;

import com.ronkadosh.bubbleup.calendar.api.dto.CalendarEventResponse;
import com.ronkadosh.bubbleup.calendar.api.dto.CreateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.api.dto.UpdateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.application.CalendarCommandService;
import com.ronkadosh.bubbleup.calendar.application.CalendarQueryService;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.CALENDAR_BASE + "/events")
@RequiredArgsConstructor
public class CalendarEventController {

    private final CalendarCommandService commands;
    private final CalendarQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 20, windowSeconds = 60, scope = RateLimitScope.PER_USER)
    public ApiResponse<CalendarEventResponse> create(@Valid @RequestBody CreateCalendarEventRequest request) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.create(request, me));
    }

    /**
     * Lists events for the given owner. When `from`/`to` are omitted, defaults to
     * the current calendar month (UTC). Events overlapping the window are returned.
     */
    @GetMapping
    public ApiResponse<List<CalendarEventResponse>> list(
            @RequestParam CalendarOwnerType ownerType,
            @RequestParam UUID ownerId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.list(ownerType, ownerId, from, to, me));
    }

    @GetMapping("/{id}")
    public ApiResponse<CalendarEventResponse> get(@PathVariable UUID id) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.get(id, me));
    }

    @PatchMapping("/{id}")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimitScope.PER_USER)
    public ApiResponse<CalendarEventResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCalendarEventRequest request
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.update(id, request, me));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        UUID me = currentUserProvider.get().id();
        commands.delete(id, me);
        return ApiResponse.ok();
    }
}
