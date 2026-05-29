package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Rejects file types that are typically executable or scriptable. Belt-and-suspenders:
 * blocks by extension AND by MIME-type. Either match → reject.
 */
@Component
public class GroupFileTypeFilter {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".com", ".sh", ".ps1", ".scr", ".vbs",
            ".jar", ".msi", ".dll", ".app", ".deb", ".rpm", ".apk"
    );

    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-msdos-program",
            "application/x-msi",
            "application/x-sh",
            "application/x-shellscript",
            "application/x-bat",
            "application/x-bash",
            "application/java-archive",
            "application/vnd.microsoft.portable-executable",
            "application/x-executable",
            "application/x-dosexec"
    );

    public void requireAllowed(String filename, String contentType) {
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            for (String ext : BLOCKED_EXTENSIONS) {
                if (lower.endsWith(ext)) {
                    throw new AppException(ErrorCode.FILE_TYPE_BLOCKED);
                }
            }
        }
        if (contentType != null
                && BLOCKED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.FILE_TYPE_BLOCKED);
        }
    }
}
