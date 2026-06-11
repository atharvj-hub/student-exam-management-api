package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.dto.response.StudentPerformanceSummaryResponse;
import com.internship.student_exam_api.exception.RateLimitExceededException;
import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.internship.student_exam_api.security.permission.Permission;
import com.internship.student_exam_api.service.RateLimitService;
import com.internship.student_exam_api.service.StudentAnalyticsService;
import com.internship.student_exam_api.service.StudentInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "Student performance analytics — deterministic summary and AI-generated insights")
public class AnalyticsController {

    private final StudentInsightService insightService;
    private final StudentAnalyticsService analyticsService;
    private final RateLimitService rateLimitService;

    @GetMapping("/students/{studentId}/summary")
    @RequirePermission(Permission.ANALYTICS_VIEW)
    @Operation(summary = "Get student performance summary",
               description = "Returns computed statistics — exam trajectory, subject aggregates, pass rate, trend, and cohort comparison. No LLM call; always available.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary returned"),
        @ApiResponse(responseCode = "404", description = "Student not found or has no recorded results"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<StudentPerformanceSummaryResponse> getSummary(
            @PathVariable Long studentId, HttpServletRequest request) {
        enforceAnalyticsRateLimit(request);
        return ResponseEntity.ok(analyticsService.getSummary(studentId));
    }

    @GetMapping("/students/{studentId}/insights")
    @RequirePermission(Permission.AI_INSIGHTS_VIEW)
    @Operation(summary = "Get AI-generated student insights",
               description = "Generates a structured narrative assessment via an LLM. Requires AI_INSIGHTS_VIEW permission. Response time depends on the AI provider.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Insights returned"),
        @ApiResponse(responseCode = "404", description = "Student not found or has no recorded results"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "502", description = "AI provider returned unusable response"),
        @ApiResponse(responseCode = "503", description = "AI provider unavailable")
    })
    public ResponseEntity<StudentInsightsResponse> getInsights(
            @PathVariable Long studentId, HttpServletRequest request) {
        enforceAnalyticsRateLimit(request);
        return ResponseEntity.ok(insightService.generateInsight(studentId));
    }

    private void enforceAnalyticsRateLimit(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (!rateLimitService.resolveAnalyticsBucket(ip).tryConsume(1)) {
            log.warn("Analytics rate limit exceeded for IP: {}", ip);
            throw new RateLimitExceededException("Analytics rate limit exceeded. Please try again later.");
        }
    }
}
