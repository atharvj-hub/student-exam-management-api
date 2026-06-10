package com.internship.student_exam_api.repository.projection;

import java.math.BigDecimal;

public interface SubjectAggregateProjection {
    String getSubjectCode();
    String getSubjectName();
    Long getExamCount();
    BigDecimal getAveragePercentage();
    BigDecimal getMinPercentage();
    BigDecimal getMaxPercentage();
}
