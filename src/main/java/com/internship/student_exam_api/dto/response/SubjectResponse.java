package com.internship.student_exam_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResponse {
    private Long id;
    private String subjectName;
    private String subjectCode;
    private Integer totalMarks;
    private LocalDateTime createdAt;
}
