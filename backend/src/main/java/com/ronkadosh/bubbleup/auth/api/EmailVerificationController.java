package com.ronkadosh.bubbleup.auth.api;

import com.ronkadosh.bubbleup.auth.api.dto.RequestVerificationEmailRequest;
import com.ronkadosh.bubbleup.auth.api.dto.VerifyEmailRequest;
import com.ronkadosh.bubbleup.auth.api.dto.VerifyEmailResponse;
import com.ronkadosh.bubbleup.auth.application.EmailVerificationService;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two-endpoint dance for "prove you own this {@code .ac.il} mailbox".
 *
 * <ul>
 *   <li>{@code POST /api/auth/verify-email/request} (authed) — send a link.</li>
 *   <li>{@code POST /api/auth/verify-email/confirm} (public) — redeem the
 *       token from the link. No auth needed; the token is the proof.</li>
 * </ul>
 *
 * <p>The frontend hits the second one when the user lands on
 * {@code /auth/verify?token=...} from clicking the email — they may or may
 * not still be logged in in that browser tab.
 */
@RestController
@RequestMapping(ApiPaths.AUTH_BASE + "/verify-email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/request")
    public ApiResponse<Void> request(@Valid @RequestBody RequestVerificationEmailRequest request) {
        var me = currentUserProvider.get();
        emailVerificationService.requestVerification(me.id(), request.academicEmail());
        return ApiResponse.success(null);
    }

    @PostMapping("/confirm")
    public ApiResponse<VerifyEmailResponse> confirm(@Valid @RequestBody VerifyEmailRequest request) {
        User user = emailVerificationService.redeem(request.token());
        return ApiResponse.success(new VerifyEmailResponse(user.isEmailVerified(), user.getEmail()));
    }
}
