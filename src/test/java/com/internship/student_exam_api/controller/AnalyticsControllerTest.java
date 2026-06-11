package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.response.CohortSummary;
import com.internship.student_exam_api.dto.response.StudentInsightPayload;
import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.dto.response.StudentPerformanceSummaryResponse;
import com.internship.student_exam_api.enums.AnalysisType;
import com.internship.student_exam_api.enums.ConfidenceLevel;
import com.internship.student_exam_api.enums.PerformanceProfile;
import com.internship.student_exam_api.enums.PerformanceTrend;
import com.internship.student_exam_api.exception.AiAnalysisException;
import com.internship.student_exam_api.exception.AiProviderUnavailableException;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.security.JwtUtil;
import com.internship.student_exam_api.security.UserDetailsServiceImpl;
import com.internship.student_exam_api.service.RateLimitService;
import com.internship.student_exam_api.service.StudentAnalyticsService;
import com.internship.student_exam_api.service.StudentInsightService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@WithMockUser(roles = "ADMIN")
@org.springframework.context.annotation.Import(com.internship.student_exam_api.security.context.JwtRequestContextFilter.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private com.internship.student_exam_api.security.context.JwtRequestContext jwtRequestContext;

    @MockBean
    private StudentAnalyticsService analyticsService;

    @MockBean
    private StudentInsightService insightService;

    @MockBean
    private RateLimitService rateLimitService;

    private Bucket allowBucket;
    private Bucket denyBucket;

    @BeforeEach
    void setUp() {
        allowBucket = Mockito.mock(Bucket.class);
        when(allowBucket.tryConsume(1)).thenReturn(true);

        denyBucket = Mockito.mock(Bucket.class);
        when(denyBucket.tryConsume(1)).thenReturn(false);

        // Default: allow all analytics requests
        when(rateLimitService.resolveAnalyticsBucket(anyString())).thenReturn(allowBucket);
    }

    // ── Summary — happy path ──────────────────────────────────────────────────

    @Test
    void getSummary_returnsSummaryResponse() throws Exception {
        when(analyticsService.getSummary(1L)).thenReturn(summaryResponse());

        mockMvc.perform(get("/api/analytics/students/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.overallTrend").value("IMPROVING"))
                .andExpect(jsonPath("$.overallAverage").value(78.50));
    }

    @Test
    void getSummary_studentNotFound_returnsNotFound() throws Exception {
        when(analyticsService.getSummary(99L))
                .thenThrow(new ResourceNotFoundException("Student", 99L));

        mockMvc.perform(get("/api/analytics/students/99/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void getSummary_rateLimitExceeded_returns429() throws Exception {
        when(rateLimitService.resolveAnalyticsBucket(anyString())).thenReturn(denyBucket);

        mockMvc.perform(get("/api/analytics/students/1/summary"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    // ── Insights — happy path ─────────────────────────────────────────────────

    @Test
    void getInsights_returnsInsightsResponse() throws Exception {
        when(insightService.generateInsight(1L)).thenReturn(insightsResponse());

        mockMvc.perform(get("/api/analytics/students/1/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.fromCache").value(false));
    }

    @Test
    void getInsights_studentNotFound_returnsNotFound() throws Exception {
        when(insightService.generateInsight(99L))
                .thenThrow(new ResourceNotFoundException("Student", 99L));

        mockMvc.perform(get("/api/analytics/students/99/insights"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void getInsights_rateLimitExceeded_returns429() throws Exception {
        when(rateLimitService.resolveAnalyticsBucket(anyString())).thenReturn(denyBucket);

        mockMvc.perform(get("/api/analytics/students/1/insights"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    // ── Insights — AI failure paths ───────────────────────────────────────────

    @Test
    void getInsights_providerUnavailable_returns503() throws Exception {
        when(insightService.generateInsight(1L))
                .thenThrow(new AiProviderUnavailableException("Ollama unreachable",
                        new RuntimeException("Connection refused")));

        mockMvc.perform(get("/api/analytics/students/1/insights"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void getInsights_malformedAiResponse_returns502() throws Exception {
        when(insightService.generateInsight(1L))
                .thenThrow(new AiAnalysisException("AI response failed validation: patterns: Maximum 4 patterns allowed."));

        mockMvc.perform(get("/api/analytics/students/1/insights"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_ANALYSIS_FAILED"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StudentPerformanceSummaryResponse summaryResponse() {
        return new StudentPerformanceSummaryResponse(
                1L, "John Doe", "MCA001-A", "A",
                5L,
                BigDecimal.valueOf(78.50),
                BigDecimal.valueOf(80.00),
                PerformanceTrend.IMPROVING,
                new CohortSummary(BigDecimal.valueOf(72.00), BigDecimal.valueOf(6.50)),
                List.of(),
                Instant.now()
        );
    }

    private StudentInsightsResponse insightsResponse() {
        StudentInsightPayload payload = new StudentInsightPayload(
                "Consistent performer across subjects.",
                PerformanceProfile.SOLID,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                ConfidenceLevel.MEDIUM
        );
        return new StudentInsightsResponse(
                1L, AnalysisType.STUDENT_PERFORMANCE, false, "qwen3:8b", Instant.now(), null, payload
        );
    }
}
