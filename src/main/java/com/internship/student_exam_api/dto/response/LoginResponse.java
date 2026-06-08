package com.internship.student_exam_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Data Transfer Object for authentication response payload containing JWT token details.
 */
@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private String role;
    private String email;
    private long expiresIn;
}
