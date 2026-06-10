package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.PerformanceTrend;
import java.math.BigDecimal;

public record SubjectPerformanceSummary(
        String subjectCode,
        String subjectName,
        Long examCount,
        BigDecimal average,
        BigDecimal min,
        BigDecimal max,
        PerformanceTrend trend,
        BigDecimal deltaFromSubjectCohortAvg
) {}
