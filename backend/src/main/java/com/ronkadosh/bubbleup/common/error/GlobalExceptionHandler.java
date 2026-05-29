package com.ronkadosh.bubbleup.common.error;

import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.api.ErrorResponse;
import com.ronkadosh.bubbleup.common.api.FieldErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        ErrorResponse error = new ErrorResponse(
                code.name(),
                "Malformed request body",
                code.getCategory().name()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "expected type";
        String message = "Parameter '" + ex.getName() + "' must be a valid " + expected;
        ErrorResponse error = new ErrorResponse(code.name(), message, code.getCategory().name());
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        ErrorResponse error = new ErrorResponse(code.name(), message, code.getCategory().name());
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        ErrorResponse error = new ErrorResponse(
                code.name(),
                ex.getMessage(),
                code.getCategory().name()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        ErrorResponse error = new ErrorResponse(
                code.name(),
                ex.getMessage() != null ? ex.getMessage() : "Invalid argument",
                code.getCategory().name()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
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
        ErrorCode code = ErrorCode.FORBIDDEN;
        ErrorResponse error = new ErrorResponse(
                code.name(),
                "Access denied",
                code.getCategory().name()
        );
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        ErrorCode code = ErrorCode.FILE_TOO_LARGE;
        long maxBytes = ex.getMaxUploadSize();
        String message = maxBytes > 0
                ? "File exceeds " + (maxBytes / (1024 * 1024)) + " MB limit"
                : "File exceeds upload size limit";
        ErrorResponse error = new ErrorResponse(code.name(), message, code.getCategory().name());
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception reaching GlobalExceptionHandler", ex);
        ErrorResponse error = new ErrorResponse(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred",
                ErrorCategory.INTERNAL.name()
        );
        return ResponseEntity.internalServerError().body(ApiResponse.failure(error));
    }
}
