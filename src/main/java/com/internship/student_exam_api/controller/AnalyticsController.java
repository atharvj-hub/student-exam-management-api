package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.security.permission.Permission;
import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.internship.student_exam_api.service.StudentInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final StudentInsightService insightService;

    @GetMapping("/students/{studentId}/insights")
    @RequirePermission(Permission.AI_INSIGHTS_VIEW)
    public ResponseEntity<StudentInsightsResponse> getInsights(@PathVariable Long studentId) {
        return ResponseEntity.ok(insightService.generateInsight(studentId));
    }
}
