package com.ronkadosh.bubbleup.demo.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import com.ronkadosh.bubbleup.demo.api.dto.DemoStartResponse;
import com.ronkadosh.bubbleup.demo.application.DemoSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, no-login entry to the interactive demo. Only registered when
 * {@code app.demo.enabled} (the VPS demo deploy), so prod never exposes it.
 *
 * <ul>
 *   <li>{@code POST /start} — public; builds an isolated world and returns a guest
 *       session. Rate-limited per IP to bound world-spam between idle sweeps.</li>
 *   <li>{@code POST /heartbeat} — auth'd as the guest; keeps the world alive.</li>
 *   <li>{@code POST /end} — auth'd as the guest; eager teardown.</li>
 * </ul>
 */
@RestController
@RequestMapping(ApiPaths.DEMO_BASE)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoController {

    private final DemoSessionService demoSessionService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/start")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimitScope.PER_IP)
    public ApiResponse<DemoStartResponse> start() {
        return ApiResponse.success(demoSessionService.start());
    }

    @PostMapping("/heartbeat")
    public ApiResponse<Void> heartbeat() {
        demoSessionService.heartbeat(currentUserProvider.get().id());
        return ApiResponse.ok();
    }

    @PostMapping("/end")
    public ApiResponse<Void> end() {
        demoSessionService.end(currentUserProvider.get().id());
        return ApiResponse.ok();
    }
}
