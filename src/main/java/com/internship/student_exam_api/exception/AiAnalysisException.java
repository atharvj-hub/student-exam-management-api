package com.internship.student_exam_api.exception;

/**
 * Thrown when the AI provider returns a response that cannot be parsed or fails
 * structured-output validation. Maps to HTTP 502 Bad Gateway.
 */
public class AiAnalysisException extends RuntimeException {

    public AiAnalysisException(String message) {
        super(message);
    }

    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
