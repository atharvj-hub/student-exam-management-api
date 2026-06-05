package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for updating an existing Result.
 *
 * INTENTIONALLY contains ONLY marks. studentId and examId are absent because:
 *   - They define the identity of a result record (who took which exam).
 *   - Re-assigning a result to a different student or exam would compromise
 *     audit integrity. If the association is wrong, delete and re-create.
 *   - Their absence makes the API contract unambiguous: PUT only changes the score.
 *
 * Any extra fields sent in the JSON body (e.g., studentId) are silently ignored
 * by Jackson's default FAIL_ON_UNKNOWN_PROPERTIES=false configuration.
 *
 * DO NOT add studentId or examId back to this DTO. See Issue #1 / architectural review.
 *
 * @see ResultCreateRequest
 */
@Getter
@Setter
@NoArgsConstructor
public class ResultUpdateRequest {

    @NotNull(message = "Marks are required")
    @Min(value = 0, message = "Marks cannot be negative")
    @Max(value = 10000, message = "Marks value is unreasonably high")
    private Double marks;
}
