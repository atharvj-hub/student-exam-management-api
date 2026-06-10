package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.AnalysisType;
import java.time.Instant;

public record StudentInsightsResponse(
        Long studentId,
        AnalysisType analysisType,
        boolean fromCache,
        String modelUsed,
        Instant analyzedAt,
        String dataHash,
        StudentInsightPayload insight
) {}
