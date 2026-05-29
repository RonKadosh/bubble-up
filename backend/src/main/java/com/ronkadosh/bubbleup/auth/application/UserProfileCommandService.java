package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.api.dto.UpdateProfileRequest;
import com.ronkadosh.bubbleup.auth.api.dto.UserProfileResponse;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.DepartmentRef;
import com.ronkadosh.bubbleup.catalog.internal.dto.UniversityRef;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileAccessPolicy;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.common.file.FileUploadRequest;
import com.ronkadosh.bubbleup.common.file.StoredFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
@Slf4j
public class UserProfileCommandService {

    private final UserRepository userRepository;
    private final CatalogInternalService catalogInternalService;
    private final FileStorageService fileStorageService;
    private final AvatarTypeFilter avatarTypeFilter;
    private final TransactionTemplate transactionTemplate;

    public UserProfileCommandService(
            UserRepository userRepository,
            CatalogInternalService catalogInternalService,
            FileStorageService fileStorageService,
            AvatarTypeFilter avatarTypeFilter,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.catalogInternalService = catalogInternalService;
        this.fileStorageService = fileStorageService;
        this.avatarTypeFilter = avatarTypeFilter;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (req.universityId() != null && !catalogInternalService.universityExists(req.universityId())) {
            throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND);
        }

        if (req.departmentId() != null) {
            UUID resolvedUniversityId = req.universityId() != null ? req.universityId() : user.getUniversityId();
            if (resolvedUniversityId == null) {
                throw new AppException(ErrorCode.UNIVERSITY_NOT_FOUND,
                        "Cannot set departmentId without a universityId");
            }
            if (!catalogInternalService.departmentExists(req.departmentId())) {
                throw new AppException(ErrorCode.DEPARTMENT_NOT_FOUND);
            }
            if (!catalogInternalService.departmentBelongsToUniversity(req.departmentId(), resolvedUniversityId)) {
                throw new AppException(ErrorCode.DEPARTMENT_NOT_IN_UNIVERSITY);
            }
        }

        if (req.displayName() != null) {
            // @Pattern + @Size guarantee non-control / 1..100 chars; trim for safety.
            user.setDisplayName(req.displayName().trim());
        }
        if (req.bio() != null) {
            // Empty string is allowed and means "clear the bio". Trim to avoid leading/trailing whitespace.
            String bio = req.bio().trim();
            user.setBio(bio.isEmpty() ? null : bio);
        }
        if (req.universityId() != null) {
            user.setUniversityId(req.universityId());
        }
        if (req.departmentId() != null) {
            user.setDepartmentId(req.departmentId());
        }
        if (req.enrollmentYear() != null) {
            user.setEnrollmentYear(req.enrollmentYear());
        }
        userRepository.save(user);
        return assembleResponse(user);
    }

    /**
     * Atomic-as-possible avatar replace. The upload happens outside the DB
     * transaction so a failure leaves no orphan row; the swap is its own
     * tight transaction; the old file is best-effort-deleted only after the
     * swap commits. If anything between upload and commit throws, the
     * just-uploaded file is deleted to avoid leak.
     */
    public UserProfileResponse replaceAvatar(UUID userId, byte[] bytes, String contentType, String originalName) {
        avatarTypeFilter.requireAllowed(contentType, bytes == null ? 0L : bytes.length);
        StoredFile uploaded = fileStorageService.upload(new FileUploadRequest(
                originalName == null ? "avatar" : originalName,
                contentType,
                bytes,
                FileAccessPolicy.PUBLIC));
        SwapResult result;
        try {
            result = transactionTemplate.execute(status -> swapAvatar(userId, uploaded.fileId(), contentType));
        } catch (RuntimeException ex) {
            bestEffortDelete(uploaded.fileId());
            throw ex;
        }
        if (result != null && result.oldFileId() != null) bestEffortDelete(result.oldFileId());
        return assembleResponse(result == null ? loadUser(userId) : result.user());
    }

    /**
     * Clear the user's avatar. Best-effort deletes the underlying file after
     * the swap-to-null commits. Idempotent — no-op if already cleared.
     */
    public UserProfileResponse deleteAvatar(UUID userId) {
        SwapResult result = transactionTemplate.execute(status -> swapAvatar(userId, null, null));
        if (result != null && result.oldFileId() != null) bestEffortDelete(result.oldFileId());
        return assembleResponse(result == null ? loadUser(userId) : result.user());
    }

    private SwapResult swapAvatar(UUID userId, String newFileId, String newContentType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String oldFileId = user.getAvatarFileId();
        user.setAvatarFileId(newFileId);
        user.setAvatarContentType(newFileId == null ? null : newContentType);
        userRepository.save(user);
        return new SwapResult(user, oldFileId);
    }

    private record SwapResult(User user, String oldFileId) {}

    private void bestEffortDelete(String fileId) {
        try {
            fileStorageService.delete(fileId);
        } catch (Exception e) {
            log.warn("Best-effort delete of avatar file {} failed: {}", fileId, e.getMessage());
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * Build the wire response with denormalized university / department names so
     * callers (controller, register flow) don't need a second read pass.
     */
    private UserProfileResponse assembleResponse(User user) {
        UserProfile profile = new UserProfile(
                user.getId(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarFileId(),
                user.getUniversityId(),
                user.getDepartmentId(),
                user.getEnrollmentYear());
        String universityName = user.getUniversityId() == null ? null
                : catalogInternalService.getUniversity(user.getUniversityId())
                        .map(UniversityRef::name).orElse(null);
        String departmentName = user.getDepartmentId() == null ? null
                : catalogInternalService.getDepartment(user.getDepartmentId())
                        .map(DepartmentRef::name).orElse(null);
        return UserProfileResponse.from(profile, universityName, departmentName, user.getRole(), user.getCreatedAt());
    }
}
