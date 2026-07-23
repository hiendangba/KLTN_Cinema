package com.cinema.exception;

import com.cinema.dto.response.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<APIResponse<Void>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        log.warn(
                "Business exception: {} - {} path={}",
                ex.getErrorCode().getCode(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", ""));
        log.warn("Business exception stack trace", ex);
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .code(ex.getErrorCode().getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        APIResponse<Map<String, String>> response = APIResponse.<Map<String, String>>builder()
                .success(false)
                .message(String.join(", ", errors.values()))
                .data(errors)
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse<Map<String, String>>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {
        log.warn("Invalid request body: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        errors.put("_error", "Request body không hợp lệ hoặc sai định dạng");
        APIResponse<Map<String, String>> response = APIResponse.<Map<String, String>>builder()
                .success(false)
                .message(errors.get("_error"))
                .data(errors)
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<APIResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            WebRequest request) {
        log.warn("Multipart request too large: {}", ex.getMessage());
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message(ErrorCode.REQUEST_TOO_LARGE.getMessage())
                .code(ErrorCode.REQUEST_TOO_LARGE.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(ErrorCode.REQUEST_TOO_LARGE.getHttpStatus()).body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<APIResponse<Void>> handleMultipartException(
            MultipartException ex,
            WebRequest request) {
        log.warn("Multipart processing failed: {}", ex.getMessage());
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message(ErrorCode.UPLOAD_FAILED.getMessage())
                .code(ErrorCode.UPLOAD_FAILED.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.warn("Method argument type mismatch: {}", ex.getMessage());
        String message = "Invalid argument type";
        if (ex.getRequiredType() != null) {
            message = "Invalid " + ex.getRequiredType().getSimpleName().toLowerCase() + " format";
        }
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message(message)
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        log.warn("No resource found: {}", ex.getMessage());
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message("Endpoint not found")
                .code(ErrorCode.NOT_FOUND.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<APIResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.warn("Method not supported: {}", ex.getMessage());
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message("Request method is not supported for this endpoint")
                .code(ErrorCode.BAD_REQUEST.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        APIResponse<Void> response = APIResponse.<Void>builder()
                .success(false)
                .message("An unexpected error occurred")
                .code(ErrorCode.INTERNAL_ERROR.getCode())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
