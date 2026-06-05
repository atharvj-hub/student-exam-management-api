package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class
ExamRequest {

    @NotBlank(message = "Exam name is required")
    private String examName;

    /**
     * API caller sends the subjectId (Long).
     * Service looks up the Subject entity from DB.
     * This is cleaner than sending the full Subject JSON in the request.
     *
     * WHY NOT accept a full Subject object here?
     *   Because the subject must already exist in DB.
     *   Accepting a full Subject object would imply you're creating a new subject
     *   at the same time, which is not the intended behavior.
     *   An ID reference makes the dependency explicit: "this exam belongs to subject #3".
     */
    @NotNull(message = "Subject ID is required")
    private Long subjectId;

    @NotNull(message = "Exam date is required")
    private LocalDate examDate;
}
