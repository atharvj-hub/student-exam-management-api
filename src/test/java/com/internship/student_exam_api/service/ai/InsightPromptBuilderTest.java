package com.internship.student_exam_api.service.ai;

import com.internship.student_exam_api.dto.response.CohortSummary;
import com.internship.student_exam_api.dto.response.StudentPerformanceSummaryResponse;
import com.internship.student_exam_api.dto.response.SubjectPerformanceSummary;
import com.internship.student_exam_api.enums.PerformanceTrend;
import com.internship.student_exam_api.repository.projection.StudentTrajectoryProjection;
import com.internship.student_exam_api.service.StudentAnalyticsService.RawAnalyticsData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightPromptBuilderTest {

    private final InsightPromptBuilder builder = new InsightPromptBuilder();

    @Test
    void buildUserPromptContext_ValidData_GeneratesContextString() {
        StudentTrajectoryProjection t1 = new TrajectoryProjectionImpl(BigDecimal.valueOf(85), "CS101", "PASS", "Midterm", LocalDate.now());
        
        SubjectPerformanceSummary s1 = new SubjectPerformanceSummary("CS101", "Computer Science", 1L, BigDecimal.valueOf(85), BigDecimal.valueOf(85), BigDecimal.valueOf(85), PerformanceTrend.STABLE, BigDecimal.valueOf(5));
        
        StudentPerformanceSummaryResponse summary = new StudentPerformanceSummaryResponse(
                1L, "Alice", "001", "A", 1L, BigDecimal.valueOf(85), BigDecimal.valueOf(100), PerformanceTrend.STABLE,
                new CohortSummary(BigDecimal.valueOf(80), BigDecimal.valueOf(5)),
                List.of(s1), Instant.now()
        );

        RawAnalyticsData data = new RawAnalyticsData(summary, List.of(t1));

        String context = builder.buildUserPromptContext(data);

        assertTrue(context.contains("=== Student Trajectory (Chronological) ==="));
        assertTrue(context.contains("CS101"));
        assertTrue(context.contains("Midterm"));
        assertTrue(context.contains("=== Subject Aggregates & Computed Trends ==="));
        assertTrue(context.contains("=== Cohort Averages ==="));
    }

    record TrajectoryProjectionImpl(BigDecimal percentage, String subjectCode, String status, String examName, LocalDate examDate) implements StudentTrajectoryProjection {
        @Override public BigDecimal getPercentage() { return percentage; }
        @Override public String getGrade() { return "A"; }
        @Override public String getStatus() { return status; }
        @Override public String getExamName() { return examName; }
        @Override public LocalDate getExamDate() { return examDate; }
        @Override public String getSubjectCode() { return subjectCode; }
        @Override public String getSubjectName() { return "Subject"; }
    }
}
