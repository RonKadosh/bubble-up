package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.room.model.Room;

import java.time.Instant;
import java.util.UUID;

/**
 * Mints JaaS (8x8.vc) JWTs that grant a specific user access to a specific room.
 * The token's claims encode the user identity (so JaaS skips its own signup
 * flow) and the moderator flag (Expert Sessions will use this in V3).
 */
public interface JitsiTokenService {

    /**
     * Sign a JaaS RS256 JWT for the given room/user.
     *
     * @param room       the BubbleUp Room being entered (used for the {@code room} claim)
     * @param userId     the BubbleUp user id (becomes {@code context.user.id})
     * @param displayName user's display name (becomes {@code context.user.name})
     * @param email      user's email (becomes {@code context.user.email})
     * @param avatarUrl  cache-busted avatar URL or null (becomes {@code context.user.avatar})
     * @param moderator  whether this user is the moderator (V1: always false; V3 Expert Sessions: true for the host)
     * @param hardExpiry hard cap on token lifetime, typically {@code event.endsAt + 30min}.
     *                   The configured {@code app.jitsi.tokenTtl} is the upper bound; whichever is sooner wins.
     */
    String issue(Room room,
                 UUID userId,
                 String displayName,
                 String email,
                 String avatarUrl,
                 boolean moderator,
                 Instant hardExpiry);
}
