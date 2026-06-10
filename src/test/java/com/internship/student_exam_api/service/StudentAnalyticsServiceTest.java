package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.response.StudentPerformanceSummaryResponse;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.enums.PerformanceTrend;
import com.internship.student_exam_api.exception.BusinessRuleException;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.repository.ResultAnalyticsRepository;
import com.internship.student_exam_api.repository.StudentRepository;
import com.internship.student_exam_api.repository.projection.SectionCohortAggregateProjection;
import com.internship.student_exam_api.repository.projection.StudentTrajectoryProjection;
import com.internship.student_exam_api.repository.projection.SubjectAggregateProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentAnalyticsServiceTest {

    @Mock
    private ResultAnalyticsRepository analyticsRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentAnalyticsService analyticsService;

    private Student student;

    @BeforeEach
    void setUp() {
        student = new Student("John Doe", "john@example.com", "MCA001-A", "A");
        student.setId(1L);
    }

    @Test
    void getSummary_StudentNotFound_ThrowsException() {
        when(studentRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> analyticsService.getSummary(1L));
    }

    @Test
    void getSummary_NoResults_ThrowsException() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(analyticsRepository.findTrajectoryByStudentId(1L)).thenReturn(Collections.emptyList());
        assertThrows(BusinessRuleException.class, () -> analyticsService.getSummary(1L));
    }

    @Test
    void getSummary_Success() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        
        StudentTrajectoryProjection t1 = new TrajectoryProjectionImpl(BigDecimal.valueOf(50), "MATH", "PASS");
        StudentTrajectoryProjection t2 = new TrajectoryProjectionImpl(BigDecimal.valueOf(80), "MATH", "PASS");
        StudentTrajectoryProjection t3 = new TrajectoryProjectionImpl(BigDecimal.valueOf(90), "MATH", "PASS");
        when(analyticsRepository.findTrajectoryByStudentId(1L)).thenReturn(List.of(t1, t2, t3));

        SubjectAggregateProjection s1 = new SubjectAggregateProjectionImpl("MATH", 3L, BigDecimal.valueOf(73.33));
        when(analyticsRepository.findSubjectAggregatesByStudentId(1L)).thenReturn(List.of(s1));

        when(analyticsRepository.findOverallSectionAverage("A")).thenReturn(BigDecimal.valueOf(70.0));
        
        SectionCohortAggregateProjection c1 = new SectionCohortAggregateProjectionImpl("MATH", BigDecimal.valueOf(75.0));
        when(analyticsRepository.findSectionSubjectAggregates("A")).thenReturn(List.of(c1));

        StudentPerformanceSummaryResponse response = analyticsService.getSummary(1L);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(73.33), response.overallAverage());
        assertEquals(PerformanceTrend.IMPROVING, response.overallTrend());
        assertEquals(1, response.subjects().size());
    }

    @Test
    void getRawData_Success() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        
        StudentTrajectoryProjection t1 = new TrajectoryProjectionImpl(BigDecimal.valueOf(50), "MATH", "PASS");
        when(analyticsRepository.findTrajectoryByStudentId(1L)).thenReturn(List.of(t1));

        SubjectAggregateProjection s1 = new SubjectAggregateProjectionImpl("MATH", 1L, BigDecimal.valueOf(50));
        when(analyticsRepository.findSubjectAggregatesByStudentId(1L)).thenReturn(List.of(s1));

        when(analyticsRepository.findOverallSectionAverage("A")).thenReturn(BigDecimal.valueOf(70.0));
        
        SectionCohortAggregateProjection c1 = new SectionCohortAggregateProjectionImpl("MATH", BigDecimal.valueOf(75.0));
        when(analyticsRepository.findSectionSubjectAggregates("A")).thenReturn(List.of(c1));

        StudentAnalyticsService.RawAnalyticsData rawData = analyticsService.getRawData(1L);

        assertNotNull(rawData);
        assertNotNull(rawData.summary());
        assertEquals(1, rawData.trajectory().size());
    }

    // --- Helper classes to mock projections ---
    record TrajectoryProjectionImpl(BigDecimal percentage, String subjectCode, String status) implements StudentTrajectoryProjection {
        @Override public BigDecimal getPercentage() { return percentage; }
        @Override public String getGrade() { return "A"; }
        @Override public String getStatus() { return status; }
        @Override public String getExamName() { return "Test"; }
        @Override public LocalDate getExamDate() { return LocalDate.now(); }
        @Override public String getSubjectCode() { return subjectCode; }
        @Override public String getSubjectName() { return "Subject"; }
    }

    record SubjectAggregateProjectionImpl(String subjectCode, Long examCount, BigDecimal averagePercentage) implements SubjectAggregateProjection {
        @Override public String getSubjectCode() { return subjectCode; }
        @Override public String getSubjectName() { return "Subject"; }
        @Override public Long getExamCount() { return examCount; }
        @Override public BigDecimal getAveragePercentage() { return averagePercentage; }
        @Override public BigDecimal getMinPercentage() { return BigDecimal.ZERO; }
        @Override public BigDecimal getMaxPercentage() { return BigDecimal.valueOf(100); }
    }

    record SectionCohortAggregateProjectionImpl(String subjectCode, BigDecimal averagePercentage) implements SectionCohortAggregateProjection {
        @Override public String getSubjectCode() { return subjectCode; }
        @Override public BigDecimal getAveragePercentage() { return averagePercentage; }
    }
}
