package com.internship.student_exam_api.exception;

import com.internship.student_exam_api.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════
 * @RestControllerAdvice — Global Exception Interceptor
 * ═══════════════════════════════════════════════════════════════
 *
 * Any exception thrown anywhere in any @Controller or @Service
 * is intercepted HERE, matched to the correct @ExceptionHandler method,
 * and returns a clean, structured JSON error response.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── 404 Not Found ────────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse.builder()
                .status(404)
                .error("NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ─── 409 Conflict ─────────────────────────────────────────────────────────
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse.builder()
                .status(409)
                .error("CONFLICT")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ─── 400 Bad Request (Business Rule) ─────────────────────────────────────
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse.builder()
                .status(400)
                .error("BAD_REQUEST")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ─── 401 Unauthorized — Bad Credentials ──────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: bad credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse.builder()
                .status(401)
                .error("UNAUTHORIZED")
                .message("Invalid email or password")
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ─── 403 Forbidden — Insufficient Role ────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiErrorResponse.builder()
                .status(403)
                .error("FORBIDDEN")
                .message("You do not have permission to perform this action")
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ─── 401 Unauthorized — Missing/Invalid Auth ──────────────────────────────
    @ExceptionHandler(com.internship.student_exam_api.security.exception.MissingAuthorizationHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingAuth(com.internship.student_exam_api.security.exception.MissingAuthorizationHeaderException ex) {
        log.warn("Missing auth: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse.builder()
                .status(401)
                .error("UNAUTHORIZED")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    // ─── 403 Forbidden — Insufficient Permission ──────────────────────────────
    @ExceptionHandler(com.internship.student_exam_api.security.exception.InsufficientPermissionException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientPermission(com.internship.student_exam_api.security.exception.InsufficientPermissionException ex) {
        log.warn("Permission denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiErrorResponse.builder()
                .status(403)
                .error("FORBIDDEN")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    /**
     * ─── 422 Unprocessable Entity — @Valid Validation Failure ───────────────
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failed: {}", validationErrors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiErrorResponse.builder()
                .status(422)
                .error("VALIDATION_FAILED")
                .message("Validation failed. Check validationErrors for details.")
                .timestamp(LocalDateTime.now())
                .validationErrors(validationErrors)
                .build()
        );
    }

    /**
     * ─── 409 Conflict — DB Constraint Violation ──────────────────────────────
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse.builder()
                .status(409)
                .error("CONFLICT")
                .message("A record with these unique details already exists or violates a database constraint.")
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    /**
     * ─── 500 Internal Server Error — Catch-All ───────────────────────────────
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse.builder()
                .status(500)
                .error("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .timestamp(LocalDateTime.now())
                .build()
        );
    }
}
