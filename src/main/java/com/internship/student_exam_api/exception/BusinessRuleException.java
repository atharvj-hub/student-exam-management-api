package com.internship.student_exam_api.exception;

/**
 * Thrown when a business rule is violated.
 * Maps to HTTP 400 Bad Request in GlobalExceptionHandler.
 *
 * Usage:
 *   throw new BusinessRuleException("Marks (110) cannot exceed total marks (100)");
 *   throw new BusinessRuleException("Result already recorded for this student and exam");
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
