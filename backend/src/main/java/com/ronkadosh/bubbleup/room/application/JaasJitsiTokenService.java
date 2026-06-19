package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.common.config.DemoProperties;
import com.ronkadosh.bubbleup.common.config.JitsiProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.room.model.Room;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JaasJitsiTokenService implements JitsiTokenService {

    private final JitsiProperties jitsiProperties;
    private final TimeProvider timeProvider;
    private final DemoProperties demoProperties;

    @Override
    public String issue(Room room,
                        UUID userId,
                        String displayName,
                        String email,
                        String avatarUrl,
                        boolean moderator,
                        Instant hardExpiry) {
        if (!jitsiProperties.isConfigured()) {
            // Demo deploy runs with blank JaaS creds on purpose: video is mocked. Return a
            // null token instead of failing so the room stays enterable — the frontend renders
            // a "video is off in the demo" placeholder when there's no appId/jwt.
            if (demoProperties.enabled()) {
                return null;
            }
            throw new AppException(ErrorCode.JITSI_NOT_CONFIGURED);
        }

        Instant now = timeProvider.now();
        Duration ttl = jitsiProperties.tokenTtl() != null ? jitsiProperties.tokenTtl() : Duration.ofHours(2);
        Instant ttlExpiry = now.plus(ttl);
        // Optional caller-supplied hard cap; whichever is sooner wins. RoomQueryService
        // now passes null (rooms may run past their scheduled endsAt while occupied), so
        // the token is bounded purely by tokenTtl and re-minted on each GET. A hardExpiry
        // is still honored if a future caller wants a firm per-token ceiling.
        Instant effectiveExpiry = (hardExpiry != null && hardExpiry.isBefore(ttlExpiry)) ? hardExpiry : ttlExpiry;

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId.toString());
        user.put("name", displayName != null ? displayName : "");
        user.put("email", email != null ? email : "");
        if (avatarUrl != null) {
            user.put("avatar", avatarUrl);
        }
        user.put("moderator", moderator);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("livestreaming", false);
        features.put("recording", false);
        features.put("transcription", false);
        features.put("outbound-call", false);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user", user);
        context.put("features", features);

        return Jwts.builder()
                .header()
                    .add("kid", jitsiProperties.kid())
                    .add("typ", "JWT")
                .and()
                .issuer("chat")
                .audience().single("jitsi")
                .subject(jitsiProperties.appId())
                .claim("room", room.getJitsiRoomName())
                .claim("context", context)
                .issuedAt(java.util.Date.from(now))
                .notBefore(java.util.Date.from(now.minusSeconds(10)))
                .expiration(java.util.Date.from(effectiveExpiry))
                .signWith(loadPrivateKey(jitsiProperties.privateKeyPem()), Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey loadPrivateKey(String pem) {
        String body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new AppException(ErrorCode.JITSI_NOT_CONFIGURED, "Invalid JaaS private key: " + e.getMessage());
        }
    }
}
