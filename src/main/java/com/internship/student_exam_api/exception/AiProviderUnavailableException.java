package com.internship.student_exam_api.exception;

/**
 * Thrown when the AI provider (Ollama / OpenAI) cannot be reached or times out.
 * Maps to HTTP 503 Service Unavailable.
 */
public class AiProviderUnavailableException extends RuntimeException {

    public AiProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
