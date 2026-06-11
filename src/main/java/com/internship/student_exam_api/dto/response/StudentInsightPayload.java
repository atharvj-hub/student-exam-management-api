package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.ConfidenceLevel;
import com.internship.student_exam_api.enums.PerformanceProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StudentInsightPayload(

        @NotBlank(message = "Overall assessment is required.")
        @Size(max = 600, message = "Overall assessment must be max 600 characters.")
        String overallAssessment,

        @NotNull(message = "Performance profile is required.")
        PerformanceProfile performanceProfile,

        @NotNull(message = "Patterns list is required.")
        @Valid
        @Size(max = 4, message = "Maximum 4 patterns allowed.")
        List<PerformancePattern> patterns,

        @NotNull(message = "Subject insights list is required.")
        @Valid
        List<SubjectInsight> subjectInsights,

        @NotNull(message = "Interventions list is required.")
        @Size(max = 3, message = "Maximum 3 interventions allowed.")
        List<String> interventions,

        boolean atRisk,

        @NotNull(message = "Confidence level is required.")
        ConfidenceLevel confidence
) {}
