package com.ronkadosh.bubbleup.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // Generic
    RESOURCE_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    VALIDATION_ERROR(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(ErrorCategory.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR),
    FORBIDDEN(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    METHOD_NOT_ALLOWED(ErrorCategory.VALIDATION, HttpStatus.METHOD_NOT_ALLOWED),
    TOO_MANY_REQUESTS(ErrorCategory.RATE_LIMIT, HttpStatus.TOO_MANY_REQUESTS),

    // Auth
    INVALID_CREDENTIALS(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    UNAUTHORIZED(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    INVALID_REFRESH_TOKEN(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    ACCOUNT_SUSPENDED(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    ACCOUNT_BANNED(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Groups
    GROUP_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    GROUP_IS_FULL(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    GROUP_NOT_PUBLIC(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    GROUP_NOT_EMPTY(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    GROUP_NOT_ACTIVE(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    NOT_GROUP_MEMBER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    NOT_GROUP_OWNER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    ALREADY_GROUP_MEMBER(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    OWNER_MUST_TRANSFER_OR_EMPTY(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    NEW_OWNER_NOT_GROUP_MEMBER(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CANNOT_REMOVE_SELF_USE_LEAVE(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // Group files
    FILE_TOO_LARGE(ErrorCategory.VALIDATION, HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_BLOCKED(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    GROUP_FILE_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    NOT_FILE_UPLOADER_OR_GROUP_OWNER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Group folders
    FOLDER_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    FOLDER_NOT_EMPTY(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    FOLDER_NAME_INVALID(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    FOLDER_NAME_TAKEN(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    NOT_FOLDER_CREATOR_OR_GROUP_OWNER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Chat
    CHAT_ROOM_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    CHAT_CURSOR_NOT_FOUND(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CHAT_MESSAGE_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    CHAT_MESSAGE_NOT_IN_ROOM(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    POLL_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    POLL_CLOSED(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    POLL_OPTION_INVALID(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    POLL_INVALID_VOTE_COUNT(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    NOT_POLL_OWNER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Calendar
    CALENDAR_EVENT_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    NOT_EVENT_AUTHOR_OR_OWNER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    INVALID_EVENT_TIME_RANGE(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    EVENT_STARTS_IN_PAST(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    EVENT_ALREADY_STARTED(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Catalog (universities / departments / courses / terms / offerings)
    UNIVERSITY_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    DEPARTMENT_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    COURSE_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    TERM_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    OFFERING_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    CURRENT_TERM_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    DEPARTMENT_NOT_IN_UNIVERSITY(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // User affiliation
    USER_AFFILIATION_REQUIRED(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // User profile / avatar
    PROFILE_NOT_VISIBLE(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    AVATAR_TYPE_NOT_ALLOWED(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    AVATAR_TOO_LARGE(ErrorCategory.VALIDATION, HttpStatus.PAYLOAD_TOO_LARGE),
    AVATAR_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),

    // Matching / Quiz
    QUIZ_QUESTION_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    QUIZ_ANSWER_INVALID(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // Room (live video/audio + whiteboard sessions)
    ROOM_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    ROOM_REQUIRES_STUDY_SESSION_EVENT(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    ROOM_NOT_YET_OPEN(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    ROOM_ENDED(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    JITSI_NOT_CONFIGURED(ErrorCategory.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR),

    // Expert profiles
    EXPERT_PROFILE_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    EXPERT_APPLICATION_ALREADY_SUBMITTED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    EXPERT_NOT_VERIFIED(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Expert sessions
    EXPERT_SESSION_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    EXPERT_SESSION_NOT_HOST(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    EXPERT_SESSION_GROUP_ALREADY_ENROLLED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    EXPERT_SESSION_GROUP_NOT_ENROLLED(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    EXPERT_SESSION_CAPACITY_REACHED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    EXPERT_SESSION_WHITEBOARD_READ_ONLY(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    EXPERT_SESSION_CANCELLED(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    EXPERT_SESSION_ENROLLMENT_CLOSED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    GROUP_SCHEDULE_CONFLICT(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),

    // Expert booking requests
    BOOKING_REQUEST_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    BOOKING_REQUEST_ALREADY_DECIDED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    NOT_BOOKING_REQUEST_REQUESTER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    NOT_BOOKING_REQUEST_EXPERT(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),

    // Admin panel — cross-cutting safety
    ADMIN_FORBIDDEN_SELF_ACTION(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    ADMIN_LAST_ADMIN_PROTECTED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_REASON_REQUIRED(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // Admin panel — expert verification
    ADMIN_EXPERT_PROFILE_NOT_PENDING(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),

    // Admin panel — catalog deletion / validation guards
    ADMIN_CATALOG_UNIVERSITY_HAS_DEPENDENTS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_DEPARTMENT_HAS_COURSES(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_TERM_HAS_OFFERINGS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_TERM_DATES_INVALID(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    ADMIN_CATALOG_COURSE_HAS_ACTIVE_GROUPS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_OFFERING_HAS_GROUPS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_COURSE_CODE_TAKEN(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_UNIVERSITY_SHORT_CODE_TAKEN(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_DEPARTMENT_SHORT_CODE_TAKEN(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_TERM_CODE_TAKEN(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_OFFERING_ALREADY_EXISTS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_COURSE_DEPARTMENT_ALREADY_LINKED(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ADMIN_CATALOG_COURSE_DEPARTMENT_NOT_LINKED(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),

    // Enrollment
    ENROLLMENT_ALREADY_EXISTS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    ENROLLMENT_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    ENROLLMENT_NO_CURRENT_OFFERING(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    NOT_ENROLLED_IN_COURSE(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN);

    private final ErrorCategory category;
    private final HttpStatus httpStatus;

    ErrorCode(ErrorCategory category, HttpStatus httpStatus) {
        this.category = category;
        this.httpStatus = httpStatus;
    }

    public ErrorCategory getCategory() { return category; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
