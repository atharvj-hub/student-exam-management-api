package com.internship.student_exam_api.exception;

/**
 * Thrown when a per-IP rate limit bucket is exhausted.
 * Maps to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
