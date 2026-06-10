package com.internship.student_exam_api.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface StudentTrajectoryProjection {
    BigDecimal getPercentage();
    String getGrade();
    String getStatus();
    String getExamName();
    LocalDate getExamDate();
    String getSubjectCode();
    String getSubjectName();
}
