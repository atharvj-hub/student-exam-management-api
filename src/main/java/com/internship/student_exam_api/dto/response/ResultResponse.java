package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.Grade;
import com.internship.student_exam_api.enums.ResultStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultResponse {
    private Long id;

    /**
     * Nested DTOs — never nested raw entities.
     * The service maps entity relationships to DTO relationships.
     *
     * Client gets full context in one call:
     *   "Student Atharv scored 75/100 in Mathematics Mid-Term → 75% → Grade A → PASS"
     */
    private StudentResponse student;
    private ExamResponse exam;

    private BigDecimal marks;
    private BigDecimal percentage;
    private Grade grade;
    private ResultStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
