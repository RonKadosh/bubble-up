package com.ronkadosh.bubbleup.auth.api;

import com.ronkadosh.bubbleup.auth.api.dto.UpdateProfileRequest;
import com.ronkadosh.bubbleup.auth.api.dto.UserProfileResponse;
import com.ronkadosh.bubbleup.auth.application.UserProfileCommandService;
import com.ronkadosh.bubbleup.auth.application.UserProfileQueryService;
import com.ronkadosh.bubbleup.auth.application.UserProfileQueryService.AvatarStream;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.USERS_BASE)
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileCommandService commands;
    private final UserProfileQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me/profile")
    public ApiResponse<UserProfileResponse> getMyProfile() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getMyProfile(me.id()));
    }

    @PatchMapping("/me/profile")
    public ApiResponse<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest req) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.updateProfile(me.id(), req));
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfileResponse> getOtherProfile(@PathVariable UUID userId) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(queries.getOtherProfile(me.id(), userId));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<UserProfileResponse> uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        CurrentUser me = currentUserProvider.get();
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.AVATAR_TYPE_NOT_ALLOWED);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.AVATAR_TYPE_NOT_ALLOWED, "Could not read avatar upload");
        }
        return ApiResponse.success(
                commands.replaceAvatar(me.id(), bytes, file.getContentType(), file.getOriginalFilename()));
    }

    @DeleteMapping("/me/avatar")
    public ApiResponse<UserProfileResponse> deleteMyAvatar() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.deleteAvatar(me.id()));
    }

    /**
     * Public avatar stream. Unauthenticated by design so {@code <img src=...>}
     * works without an Authorization header. The {@code ?v={fileId}} query
     * parameter is just a cache-buster — the controller reads the actual
     * {@code avatarFileId} fresh from the DB; a stale {@code v=} value still
     * resolves to whatever the user currently has.
     */
    @GetMapping("/{userId}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID userId) {
        AvatarStream stream = queries.getAvatar(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(stream.contentType()));
        headers.setCacheControl(CacheControl
                .maxAge(Duration.ofDays(365))
                .cachePublic()
                .immutable());
        return new ResponseEntity<>(stream.bytes(), headers, 200);
    }
}
