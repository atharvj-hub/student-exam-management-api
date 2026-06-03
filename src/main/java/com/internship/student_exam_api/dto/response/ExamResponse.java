package com.internship.student_exam_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResponse {
    private Long id;
    private String examName;

    /**
     * We include the full SubjectResponse nested here.
     * This is safe because:
     *   1. We're in a DTO (no Hibernate session involved)
     *   2. The service eagerly fetches the subject before building this DTO
     *   3. No LazyInitializationException risk
     *
     * ALTERNATIVE: only include subjectId.
     * Including full subject is more useful for API consumers (one call gets all data).
     * Trade-off: slightly larger response payload.
     */
    private SubjectResponse subject;
    private LocalDate examDate;
    private LocalDateTime createdAt;
}
