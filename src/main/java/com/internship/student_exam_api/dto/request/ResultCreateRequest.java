package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for recording a new Result.
 *
 * studentId and examId are REQUIRED at creation: they define the identity
 * of the result record (one result per student per exam, enforced by DB constraint).
 * Once created, these references are IMMUTABLE — they cannot be changed via PUT.
 * To correct a student/exam assignment, delete and re-create the result.
 *
 * @see ResultUpdateRequest
 */
@Getter
@Setter
@NoArgsConstructor
public class ResultCreateRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Exam ID is required")
    private Long examId;

    @NotNull(message = "Marks are required")
    @Min(value = 0, message = "Marks cannot be negative")
    @Max(value = 10000, message = "Marks value is unreasonably high")
    private Double marks;
}
