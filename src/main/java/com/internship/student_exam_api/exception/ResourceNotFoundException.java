package com.internship.student_exam_api.exception;

/**
 * Thrown when a requested resource doesn't exist in the database.
 * Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 *
 * Usage:
 *   throw new ResourceNotFoundException("Student", 5L);
 *   → "Student not found with id: 5"
 *
 * WHY extend RuntimeException and NOT Exception?
 *   Checked exceptions (extend Exception) MUST be caught or declared
 *   in every method signature. This pollutes every service method:
 *     public Student getStudent(Long id) throws ResourceNotFoundException { ... }
 *
 *   RuntimeException (unchecked) propagates up the call stack automatically
 *   and is caught by GlobalExceptionHandler at the top.
 *   Spring's @Transactional also ONLY rolls back on RuntimeException by default.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
