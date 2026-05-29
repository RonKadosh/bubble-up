package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.api.dto.UserProfileResponse;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.DepartmentRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.UniversityRef;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileQueryService {

    private final UserRepository userRepository;
    private final CatalogInternalService catalogInternalService;
    private final GroupInternalService groupInternalService;
    private final FileStorageService fileStorageService;

    /**
     * Self-profile read. No visibility check — the caller is asking for their own data.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        return assembleResponse(loadUser(userId));
    }

    /**
     * Other-user profile read. Throws {@code PROFILE_NOT_VISIBLE} unless the
     * viewer and target share at least one group (self-view auto-passes).
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getOtherProfile(UUID viewerId, UUID targetUserId) {
        if (!groupInternalService.usersShareAnyGroup(viewerId, targetUserId)) {
            throw new AppException(ErrorCode.PROFILE_NOT_VISIBLE);
        }
        return assembleResponse(loadUser(targetUserId));
    }

    /**
     * Read the avatar bytes + the content type they were uploaded as. Throws
     * {@code AVATAR_NOT_FOUND} when the user has no avatar set. Keeps the
     * repository dependency contained in the application layer — the
     * controller calls this rather than touching {@code UserRepository}.
     */
    @Transactional(readOnly = true)
    public AvatarStream getAvatar(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.AVATAR_NOT_FOUND));
        if (user.getAvatarFileId() == null) {
            throw new AppException(ErrorCode.AVATAR_NOT_FOUND);
        }
        byte[] bytes = fileStorageService.download(user.getAvatarFileId());
        String contentType = user.getAvatarContentType() != null
                ? user.getAvatarContentType()
                : "image/jpeg";   // legacy rows; new uploads always populate the column
        return new AvatarStream(bytes, contentType);
    }

    /**
     * Tuple returned by {@link #getAvatar(UUID)}. Kept on the service to avoid
     * proliferating one-shot records in {@code application/}.
     */
    public record AvatarStream(byte[] bytes, String contentType) {}

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private UserProfileResponse assembleResponse(User user) {
        UserProfile profile = new UserProfile(
                user.getId(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarFileId(),
                user.getUniversityId(),
                user.getDepartmentId(),
                user.getEnrollmentYear());
        String universityName = user.getUniversityId() == null
                ? null
                : catalogInternalService.getUniversity(user.getUniversityId())
                        .map(UniversityRef::name).orElse(null);
        String departmentName = user.getDepartmentId() == null
                ? null
                : catalogInternalService.getDepartment(user.getDepartmentId())
                        .map(DepartmentRef::name).orElse(null);
        return UserProfileResponse.from(profile, universityName, departmentName, user.getRole(), user.getCreatedAt());
    }
}
