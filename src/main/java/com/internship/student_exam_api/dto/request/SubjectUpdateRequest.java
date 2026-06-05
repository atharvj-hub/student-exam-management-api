package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for updating an existing Subject.
 *
 * Currently identical to {@link SubjectCreateRequest}, but intentionally
 * separate to allow independent evolution of create vs. update contracts, e.g.:
 *   - Making {@code subjectCode} read-only on update (it's a natural key).
 *   - Restricting {@code totalMarks} changes (retroactive changes affect all linked results).
 *
 * DO NOT merge this class back into a shared DTO with SubjectCreateRequest.
 * The separation is intentional — see Issue #1 / architectural review.
 *
 * @see SubjectCreateRequest
 */
@Getter
@Setter
@NoArgsConstructor
public class SubjectUpdateRequest {

    @NotBlank(message = "Subject name is required")
    private String subjectName;

    @NotBlank(message = "Subject code is required")
    private String subjectCode;

    @NotNull(message = "Total marks is required")
    @Min(value = 1, message = "Total marks must be at least 1")
    private Integer totalMarks;
}
