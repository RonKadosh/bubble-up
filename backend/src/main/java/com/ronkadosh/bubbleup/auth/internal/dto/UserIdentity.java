package com.ronkadosh.bubbleup.auth.internal.dto;

import com.ronkadosh.bubbleup.common.api.ApiPaths;

import java.util.UUID;

/**
 * Cheap, always-wanted identity slice for cross-module rendering: the bits
 * needed to show a user in a list (chat sender row, member row, dashboard
 * actor) without revealing affiliation or bio.
 *
 * <p>Distinct from {@link UserProfile} on purpose. {@code UserProfile} is the
 * viewer-gated affiliation snapshot used by relevance / catalog flows;
 * {@code UserIdentity} is the public-by-design slice that every co-member
 * already sees via the chat broadcast they share.
 *
 * <p>{@code avatarFileId} is the raw storage handle (per
 * {@code FileStorageService}). Use {@link #avatarUrl()} to get the
 * cache-busted public URL that the frontend feeds into {@code <img src>}.
 */
public record UserIdentity(UUID userId, String displayName, String avatarFileId) {

    /**
     * Cache-busted public avatar URL ({@code .../avatar?v={fileId}}), or null
     * when this user has no avatar. The {@code ?v=} parameter changes on every
     * re-upload (new file id), so callers can safely set long
     * {@code Cache-Control: immutable} on the response.
     */
    public String avatarUrl() {
        if (avatarFileId == null) return null;
        return ApiPaths.USERS_BASE + "/" + userId + "/avatar?v=" + avatarFileId;
    }
}
