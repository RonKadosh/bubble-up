package com.ronkadosh.studybuddy.auth.api;

import com.ronkadosh.studybuddy.auth.api.dto.AuthResponse;
import com.ronkadosh.studybuddy.auth.api.dto.LoginRequest;
import com.ronkadosh.studybuddy.auth.api.dto.LogoutRequest;
import com.ronkadosh.studybuddy.auth.api.dto.RefreshRequest;
import com.ronkadosh.studybuddy.auth.api.dto.RegisterRequest;
import com.ronkadosh.studybuddy.auth.application.AuthService;
import com.ronkadosh.studybuddy.common.api.ApiPaths;
import com.ronkadosh.studybuddy.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.AUTH_BASE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.ok();
    }
}
