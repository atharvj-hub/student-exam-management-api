package com.internship.student_exam_api.security.exception;

public class MissingAuthorizationHeaderException extends RuntimeException {

    public MissingAuthorizationHeaderException() {
        super("No valid authorization token found in request context");
    }
}
