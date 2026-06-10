package com.internship.student_exam_api.service.ai;

import com.internship.student_exam_api.service.StudentAnalyticsService.RawAnalyticsData;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class InsightPromptBuilder {

    public String buildUserPromptContext(RawAnalyticsData data) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Student Trajectory (Chronological) ===\n");
        sb.append("[Format: Date | Subject | Exam | Percentage | Grade | Status]\n");
        String trajectoryStr = data.trajectory().stream()
                .limit(50)
                .map(t -> String.format("%s | %s | %s | %s | %s | %s",
                        t.getExamDate(), t.getSubjectCode(), t.getExamName(),
                        t.getPercentage(), t.getGrade(), t.getStatus()))
                .collect(Collectors.joining("\n"));
        sb.append(trajectoryStr).append("\n\n");

        sb.append("=== Subject Aggregates & Computed Trends ===\n");
        sb.append("[Format: Subject | Exams Taken | Average | Trend]\n");
        String aggregatesStr = data.summary().subjects().stream()
                .map(s -> String.format("%s | %d | %s | %s",
                        s.subjectCode(), s.examCount(), s.average(), s.trend()))
                .collect(Collectors.joining("\n"));
        sb.append(aggregatesStr).append("\n\n");

        sb.append("=== Cohort Averages ===\n");
        sb.append("[Format: Subject | Section Average]\n");
        String cohortStr = data.summary().subjects().stream()
                .map(s -> {
                    var cohortAvg = s.average().subtract(s.deltaFromSubjectCohortAvg());
                    return String.format("%s | %s", s.subjectCode(), cohortAvg);
                })
                .collect(Collectors.joining("\n"));
        sb.append(cohortStr).append("\n\n");

        return sb.toString();
    }
}
