package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.response.CohortSummary;
import com.internship.student_exam_api.dto.response.StudentPerformanceSummaryResponse;
import com.internship.student_exam_api.dto.response.SubjectPerformanceSummary;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.enums.PerformanceTrend;
import com.internship.student_exam_api.exception.BusinessRuleException;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.repository.ResultAnalyticsRepository;
import com.internship.student_exam_api.repository.StudentRepository;
import com.internship.student_exam_api.repository.projection.SectionCohortAggregateProjection;
import com.internship.student_exam_api.repository.projection.StudentTrajectoryProjection;
import com.internship.student_exam_api.repository.projection.SubjectAggregateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAnalyticsService {

    private final ResultAnalyticsRepository analyticsRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public StudentPerformanceSummaryResponse getSummary(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        List<StudentTrajectoryProjection> trajectory = analyticsRepository.findTrajectoryByStudentId(studentId);
        if (trajectory.isEmpty()) {
            throw new BusinessRuleException("Cannot analyze a student with no recorded results");
        }

        List<SubjectAggregateProjection> subjectAggregates = analyticsRepository.findSubjectAggregatesByStudentId(studentId);
        BigDecimal sectionAverage = analyticsRepository.findOverallSectionAverage(student.getSection());
        List<SectionCohortAggregateProjection> cohortAggregates = analyticsRepository.findSectionSubjectAggregates(student.getSection());

        Map<String, BigDecimal> cohortSubjectMap = cohortAggregates.stream()
                .collect(Collectors.toMap(
                        SectionCohortAggregateProjection::getSubjectCode,
                        SectionCohortAggregateProjection::getAveragePercentage
                ));

        BigDecimal overallAverage = subjectAggregates.stream()
                .map(SubjectAggregateProjection::getAveragePercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(subjectAggregates.size()), 2, RoundingMode.HALF_UP);

        long passedExams = trajectory.stream()
                .filter(t -> "PASS".equalsIgnoreCase(t.getStatus()))
                .count();
        BigDecimal passRate = BigDecimal.valueOf(passedExams)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(trajectory.size()), 2, RoundingMode.HALF_UP);

        PerformanceTrend overallTrend = calculateDeltaTrend(trajectory);
        BigDecimal deltaFromSection = sectionAverage != null ? overallAverage.subtract(sectionAverage) : BigDecimal.ZERO;

        List<SubjectPerformanceSummary> subjects = subjectAggregates.stream().map(agg -> {
            List<StudentTrajectoryProjection> subjTrajectory = trajectory.stream()
                    .filter(t -> t.getSubjectCode().equals(agg.getSubjectCode()))
                    .toList();
            PerformanceTrend subjTrend = calculateDeltaTrend(subjTrajectory);

            BigDecimal subjCohortAvg = cohortSubjectMap.getOrDefault(agg.getSubjectCode(), agg.getAveragePercentage());
            BigDecimal subjDelta = agg.getAveragePercentage().subtract(subjCohortAvg);

            return new SubjectPerformanceSummary(
                    agg.getSubjectCode(),
                    agg.getSubjectName(),
                    agg.getExamCount(),
                    agg.getAveragePercentage(),
                    agg.getMinPercentage(),
                    agg.getMaxPercentage(),
                    subjTrend,
                    subjDelta
            );
        }).toList();

        return new StudentPerformanceSummaryResponse(
                student.getId(),
                student.getName(),
                student.getRollNumber(),
                student.getSection(),
                (long) trajectory.size(),
                overallAverage,
                passRate,
                overallTrend,
                new CohortSummary(sectionAverage != null ? sectionAverage : BigDecimal.ZERO, deltaFromSection),
                subjects,
                Instant.now()
        );
    }

    public PerformanceTrend calculateDeltaTrend(List<StudentTrajectoryProjection> trajectory) {
        if (trajectory.size() < 3) {
            return PerformanceTrend.STABLE;
        }

        BigDecimal totalDelta = BigDecimal.ZERO;
        BigDecimal maxSwing = BigDecimal.ZERO;

        for (int i = 1; i < trajectory.size(); i++) {
            BigDecimal prev = trajectory.get(i - 1).getPercentage();
            BigDecimal curr = trajectory.get(i).getPercentage();
            BigDecimal delta = curr.subtract(prev);

            totalDelta = totalDelta.add(delta);
            BigDecimal absSwing = delta.abs();
            if (absSwing.compareTo(maxSwing) > 0) {
                maxSwing = absSwing;
            }
        }

        BigDecimal avgDelta = totalDelta.divide(BigDecimal.valueOf(trajectory.size() - 1), 2, RoundingMode.HALF_UP);

        if (avgDelta.compareTo(BigDecimal.valueOf(1.5)) > 0) {
            return PerformanceTrend.IMPROVING;
        }
        if (avgDelta.compareTo(BigDecimal.valueOf(-1.5)) < 0) {
            return PerformanceTrend.DECLINING;
        }
        if (maxSwing.compareTo(BigDecimal.valueOf(15.0)) > 0) {
            return PerformanceTrend.VOLATILE;
        }
        return PerformanceTrend.STABLE;
    }

    public RawAnalyticsData getRawData(Long studentId) {
        StudentPerformanceSummaryResponse summary = getSummary(studentId);
        List<StudentTrajectoryProjection> trajectory = analyticsRepository.findTrajectoryByStudentId(studentId);
        return new RawAnalyticsData(summary, trajectory);
    }

    public record RawAnalyticsData(
            StudentPerformanceSummaryResponse summary,
            List<StudentTrajectoryProjection> trajectory
    ) {}
}
