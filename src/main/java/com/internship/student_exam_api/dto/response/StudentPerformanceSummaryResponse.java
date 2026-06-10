package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.PerformanceTrend;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StudentPerformanceSummaryResponse(
        Long studentId,
        String name,
        String rollNumber,
        String section,
        Long examsTaken,
        BigDecimal overallAverage,
        BigDecimal passRate,
        PerformanceTrend overallTrend,
        CohortSummary cohort,
        List<SubjectPerformanceSummary> subjects,
        Instant generatedAt
) {}
