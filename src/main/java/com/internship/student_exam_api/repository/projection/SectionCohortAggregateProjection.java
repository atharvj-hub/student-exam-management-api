package com.internship.student_exam_api.repository.projection;

import java.math.BigDecimal;

public interface SectionCohortAggregateProjection {
    String getSubjectCode();
    BigDecimal getAveragePercentage();
}
