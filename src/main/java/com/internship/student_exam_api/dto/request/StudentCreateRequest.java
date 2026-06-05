package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for creating a new Student.
 *
 * Contains all fields required at enrollment time.
 * rollNumber and email are unique identifiers enforced at the DB level.
 *
 * Intentionally separate from {@link StudentUpdateRequest} to allow
 * independent evolution of create vs. update contracts (e.g., making
 * rollNumber immutable on update without affecting creation rules).
 *
 * @see StudentUpdateRequest
 */
@Getter
@Setter
@NoArgsConstructor
public class StudentCreateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Roll number assigned at enrollment — must be unique.
     * Once created, this field cannot be changed via the update endpoint.
     */
    @NotBlank(message = "Roll number is required")
    private String rollNumber;
}
