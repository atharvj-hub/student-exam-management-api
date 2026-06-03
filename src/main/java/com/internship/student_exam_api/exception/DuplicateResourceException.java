package com.internship.student_exam_api.exception;

/**
 * Thrown when trying to create a resource that already exists.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 *
 * Usage:
 *   throw new DuplicateResourceException("Student", "email", "atharv@email.com");
 *   → "Student with email 'atharv@email.com' already exists"
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String resource, String field, String value) {
        super(resource + " with " + field + " '" + value + "' already exists");
    }
}
