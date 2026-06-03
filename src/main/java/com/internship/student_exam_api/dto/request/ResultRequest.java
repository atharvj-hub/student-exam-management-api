package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResultRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @NotNull(message = "Exam ID is required")
    private Long examId;

    /**
     * Marks as Double to support decimal marks (e.g., 75.5).
     * @Min(0) → marks cannot be negative.
     * Business rule: marks <= totalMarks is validated in ResultService.
     *   We can't put that as a simple annotation here because totalMarks
     *   is on the Subject, which is only accessible after DB lookup in the service.
     */
    @NotNull(message = "Marks are required")
    @Min(value = 0, message = "Marks cannot be negative")
    private Double marks;
}
