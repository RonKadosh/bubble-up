package com.ronkadosh.studybuddy.common.error;

import com.ronkadosh.studybuddy.common.api.ApiResponse;
import com.ronkadosh.studybuddy.common.api.ErrorResponse;
import com.ronkadosh.studybuddy.common.api.FieldErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode code = ex.getErrorCode();
        ErrorResponse error = new ErrorResponse(
                code.name(),
                ex.getMessage(),
                code.getCategory().name()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorResponse> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorResponse(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorResponse error = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                "Validation failed",
                ErrorCategory.VALIDATION.name(),
                fields
        );
        return ResponseEntity.badRequest().body(ApiResponse.failure(error));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthenticationException ex) {
        ErrorResponse error = new ErrorResponse(
                ErrorCode.UNAUTHORIZED.name(),
                "Authentication required",
                ErrorCategory.UNAUTHORIZED.name()
        );
        return ResponseEntity.status(401).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                ErrorCategory.FORBIDDEN.name(),
                "Access denied",
                ErrorCategory.FORBIDDEN.name()
        );
        return ResponseEntity.status(403).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        ErrorCode code = ErrorCode.FILE_TOO_LARGE;
        ErrorResponse error = new ErrorResponse(
                code.name(),
                "File exceeds 25 MB limit",
                code.getCategory().name()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        ErrorResponse error = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred",
                ErrorCategory.INTERNAL.name()
        );
        return ResponseEntity.internalServerError().body(ApiResponse.failure(error));
    }
}
