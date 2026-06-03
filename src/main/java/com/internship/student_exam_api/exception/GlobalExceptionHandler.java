package com.internship.student_exam_api.exception;

import com.internship.student_exam_api.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * WITHOUT this class:
 *   An unhandled RuntimeException propagates through:
 *     Service → Controller → DispatcherServlet → Spring's DefaultHandlerExceptionResolver
 *   Result: HTTP 500 with a Java stack trace in the response body.
 *   Stack traces leak internal info. They're a security risk. They're ugly.
 *
 * WITH this class:
 *   Any exception thrown anywhere in any @Controller or @Service
 *   is intercepted HERE, matched to the correct @ExceptionHandler method,
 *   and returns a clean, structured JSON error response.
 *
 * HOW Spring routes exceptions here:
 *   @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *   Spring's HandlerExceptionResolver checks ALL @ExceptionHandler methods
 *   in this class and finds the most specific match for the thrown exception.
 *   If thrown exception = ResourceNotFoundException → handleResourceNotFound fires.
 *   If thrown exception = SomethingElse (no specific handler) → handleGenericException fires.
 *
 * PRIORITY:
 *   More specific handler wins:
 *   ResourceNotFoundException extends RuntimeException.
 *   If both handlers exist, Spring picks ResourceNotFoundException handler (more specific).
 *
 * @Slf4j → Lombok generates a `log` field: private static final Logger log = ...
 *   Lets you do: log.error("...", e) without any boilerplate.
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

    /**
     * ─── 422 Unprocessable Entity — @Valid Validation Failure ───────────────
     *
     * Thrown automatically by Spring when @Valid fails on a @RequestBody.
     * Contains a list of field-level errors.
     *
     * Example response:
     * {
     *   "status": 422,
     *   "error": "VALIDATION_FAILED",
     *   "message": "Validation failed",
     *   "validationErrors": {
     *     "name": "Name is required",
     *     "email": "Invalid email format"
     *   }
     * }
     *
     * MethodArgumentNotValidException.getBindingResult().getFieldErrors()
     * returns all failed fields. We collect them into a Map<fieldName, errorMessage>.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> validationErrors = new HashMap<>();

        // Each FieldError represents one failed @NotBlank / @Email / etc.
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
     * ─── 500 Internal Server Error — Catch-All ───────────────────────────────
     *
     * Catches any unhandled exception.
     * We log the full stack trace here (log.error includes the exception).
     * We return a generic message to the client — never expose internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        // Log the full stack trace for debugging — but DON'T send it to client
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
