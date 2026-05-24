package com.ronkadosh.studybuddy.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // Generic
    RESOURCE_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    VALIDATION_ERROR(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(ErrorCategory.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR),

    // Auth
    INVALID_CREDENTIALS(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_EXISTS(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    UNAUTHORIZED(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    INVALID_REFRESH_TOKEN(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(ErrorCategory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED),

    // Groups
    GROUP_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    GROUP_IS_FULL(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
    GROUP_NOT_PUBLIC(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    GROUP_NOT_EMPTY(ErrorCategory.CONFLICT, HttpStatus.CONFLICT),
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

    // Chat
    CHAT_ROOM_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    CHAT_CURSOR_NOT_FOUND(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),
    CHAT_MESSAGE_NOT_IN_ROOM(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST),

    // Calendar
    CALENDAR_EVENT_NOT_FOUND(ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND),
    NOT_EVENT_AUTHOR_OR_OWNER(ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN),
    INVALID_EVENT_TIME_RANGE(ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST);

    private final ErrorCategory category;
    private final HttpStatus httpStatus;

    ErrorCode(ErrorCategory category, HttpStatus httpStatus) {
        this.category = category;
        this.httpStatus = httpStatus;
    }

    public ErrorCategory getCategory() { return category; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
