package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Whitelist-style policy for Bubble cover-image uploads — the group-scope twin of
 * {@code AvatarTypeFilter}. Accepts only a small set of raster image MIME types and
 * caps at 5 MB so {@code FileStorageService} never sees a stray PDF or HTML blob.
 * Lives in the feature module (caps + MIME are policy, not infrastructure).
 */
@Component
public class GroupImageTypeFilter {

    /** 5 MB — matches the avatar cap. */
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
            throw new AppException(ErrorCode.GROUP_IMAGE_TYPE_NOT_ALLOWED);
        }
        if (sizeBytes > MAX_BYTES) {
            throw new AppException(ErrorCode.GROUP_IMAGE_TOO_LARGE);
        }
    }
}
