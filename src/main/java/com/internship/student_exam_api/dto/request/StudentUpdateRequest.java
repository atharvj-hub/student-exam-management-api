package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for updating an existing Student.
 *
 * Intentionally separate from {@link StudentCreateRequest} to enforce
 * immutability of certain fields after enrollment:
 *
 * - {@code rollNumber} is ABSENT: it is a natural key assigned at creation
 *   and must NOT be changed via PUT. To change a roll number, the record
 *   must be deleted and re-created.
 * - {@code collegeName} is ABSENT: this field does not exist in the
 *   Student entity or the database schema. It was removed to prevent
 *   the API from silently accepting and discarding data.
 *
 * DO NOT merge this class back into a shared DTO with StudentCreateRequest.
 * The separation is intentional — see Issue #1 / architectural review.
 *
 * @see StudentCreateRequest
 */
@Getter
@Setter
@NoArgsConstructor
public class StudentUpdateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
