package com.internship.student_exam_api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response.
 * Every error from this API returns this exact shape:
 *
 * {
 *   "status": 404,
 *   "error": "NOT_FOUND",
 *   "message": "Student not found with id: 5",
 *   "timestamp": "2024-03-15T10:30:00",
 *   "validationErrors": null  ← only present for 422 validation failures
 * }
 *
 * @JsonInclude(NON_NULL) → null fields are excluded from JSON output.
 *   validationErrors is only included when there are actual validation errors.
 *   Without this: every response would include "validationErrors": null — noise.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;

    /**
     * Only populated for validation failures (422).
     * Format: { "name": "Name is required", "email": "Invalid email" }
     */
    private Map<String, String> validationErrors;
}
