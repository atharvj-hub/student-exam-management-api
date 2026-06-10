package com.internship.student_exam_api.dto.response;

import java.math.BigDecimal;

public record CohortSummary(
        BigDecimal sectionAverage,
        BigDecimal deltaFromSection
) {}
