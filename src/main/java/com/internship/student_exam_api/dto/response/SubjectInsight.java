package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.CohortComparison;
import jakarta.validation.constraints.Size;

public record SubjectInsight(
        String subjectCode,
        
        CohortComparison relativeToCohort,
        
        @Size(max = 300, message = "Observation must be max 300 characters.")
        String observation
) {}
