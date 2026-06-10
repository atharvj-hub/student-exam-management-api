package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.ConfidenceLevel;
import com.internship.student_exam_api.enums.PerformanceProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StudentInsightPayload(
        @Size(max = 600, message = "Overall assessment must be max 600 characters.")
        String overallAssessment,
        
        PerformanceProfile performanceProfile,
        
        @Valid
        @Size(max = 4, message = "Maximum 4 patterns allowed.")
        List<PerformancePattern> patterns,
        
        @Valid
        List<SubjectInsight> subjectInsights,
        
        @Size(max = 3, message = "Maximum 3 interventions allowed.")
        List<String> interventions,
        
        boolean atRisk,
        
        ConfidenceLevel confidence
) {}
