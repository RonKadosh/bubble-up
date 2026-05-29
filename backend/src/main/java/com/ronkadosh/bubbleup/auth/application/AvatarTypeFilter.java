package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Whitelist-style policy for avatar uploads. Unlike {@code GroupFileTypeFilter}
 * (which is a blocklist for executables), avatars accept only a small set of
 * raster image MIME types and cap at 5 MB. Anything else is rejected at the
 * boundary so {@code FileStorageService} never sees a stray PDF or HTML blob.
 */
@Component
public class AvatarTypeFilter {

    /** 5 MB. Generous enough for high-DPI photos; small enough to not OOM the upload path. */
    public static final long MAX_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );

    public void requireAllowed(String contentType, long sizeBytes) {
        if (contentType == null
                || !ALLOWED_MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.AVATAR_TYPE_NOT_ALLOWED);
        }
        if (sizeBytes > MAX_BYTES) {
            throw new AppException(ErrorCode.AVATAR_TOO_LARGE);
        }
    }
}
