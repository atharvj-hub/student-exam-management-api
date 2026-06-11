package com.internship.student_exam_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.student_exam_api.dto.response.StudentInsightPayload;
import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.entity.AiAnalysisCache;
import com.internship.student_exam_api.enums.ConfidenceLevel;
import com.internship.student_exam_api.enums.PerformanceProfile;
import com.internship.student_exam_api.exception.AiAnalysisException;
import com.internship.student_exam_api.exception.AiProviderUnavailableException;
import com.internship.student_exam_api.repository.AiAnalysisCacheRepository;
import com.internship.student_exam_api.service.ai.InsightPromptBuilder;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentInsightServiceTest {

    @Mock private StudentAnalyticsService analyticsService;
    @Mock private InsightPromptBuilder promptBuilder;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private ChatClient chatClient;
    @Mock private AiAnalysisCacheRepository cacheRepository;

    // Real instances injected via ReflectionTestUtils after @InjectMocks builds the service
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StudentInsightService insightService;

    private static final String DATA_CONTEXT = "Mock Context";
    private static final String DATA_HASH = StudentInsightService.computeDataHash(DATA_CONTEXT);
    private static final String MODEL = "qwen3:8b";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(insightService, "modelName", MODEL);
        ReflectionTestUtils.setField(insightService, "validator", validator);
        ReflectionTestUtils.setField(insightService, "objectMapper", objectMapper);

        StudentAnalyticsService.RawAnalyticsData rawData =
                new StudentAnalyticsService.RawAnalyticsData(null, Collections.emptyList());
        when(analyticsService.getRawData(1L)).thenReturn(rawData);
        when(promptBuilder.buildUserPromptContext(rawData)).thenReturn(DATA_CONTEXT);

        // Default: cache miss
        when(cacheRepository.findByStudentIdAndDataHash(anyLong(), anyString()))
                .thenReturn(Optional.empty());
    }

    // ── Cache hit ─────────────────────────────────────────────────────────────

    @Test
    void generateInsight_cacheHit_doesNotCallAi() throws Exception {
        AiAnalysisCache entry = cachedEntry(1L, DATA_HASH, MODEL, validPayload());
        when(cacheRepository.findByStudentIdAndDataHash(1L, DATA_HASH)).thenReturn(Optional.of(entry));

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertTrue(response.fromCache());
        assertEquals(DATA_HASH, response.dataHash());
        assertEquals(MODEL, response.modelUsed());
        assertNotNull(response.insight());
        // AI must NOT have been called
        verify(chatClient, never()).prompt();
        verify(cacheRepository, never()).save(any());
    }

    @Test
    void generateInsight_cacheHit_analyzedAtReflectsCacheTimestamp() throws Exception {
        Instant stored = Instant.parse("2026-01-15T10:00:00Z");
        AiAnalysisCache entry = cachedEntry(1L, DATA_HASH, MODEL, validPayload());
        entry.setUpdatedAt(stored);
        when(cacheRepository.findByStudentIdAndDataHash(1L, DATA_HASH)).thenReturn(Optional.of(entry));

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertEquals(stored, response.analyzedAt());
    }

    // ── Cache miss ────────────────────────────────────────────────────────────

    @Test
    void generateInsight_cacheMiss_callsAiAndStoresCache() {
        mockAiReturning(validPayload());

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertFalse(response.fromCache());
        assertEquals(DATA_HASH, response.dataHash());
        assertEquals(MODEL, response.modelUsed());

        ArgumentCaptor<AiAnalysisCache> saved = ArgumentCaptor.forClass(AiAnalysisCache.class);
        verify(cacheRepository).save(saved.capture());
        assertEquals(1L, saved.getValue().getStudentId());
        assertEquals(DATA_HASH, saved.getValue().getDataHash());
        assertEquals(MODEL, saved.getValue().getModelUsed());
        assertNotNull(saved.getValue().getPayload());
    }

    // ── Cache invalidation: data changed ─────────────────────────────────────

    @Test
    void generateInsight_dataChanged_newHashCacheMiss() throws Exception {
        // The OLD cached entry has a different hash (stale data)
        String oldHash = StudentInsightService.computeDataHash("Old Context");
        AiAnalysisCache oldEntry = cachedEntry(1L, oldHash, MODEL, validPayload());

        // Cache lookup for the NEW hash returns empty
        when(cacheRepository.findByStudentIdAndDataHash(1L, DATA_HASH)).thenReturn(Optional.empty());
        // (The old entry is simply never found — different key)
        mockAiReturning(validPayload());

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertFalse(response.fromCache());
        assertEquals(DATA_HASH, response.dataHash());
        // New entry saved with the new hash
        ArgumentCaptor<AiAnalysisCache> saved = ArgumentCaptor.forClass(AiAnalysisCache.class);
        verify(cacheRepository).save(saved.capture());
        assertEquals(DATA_HASH, saved.getValue().getDataHash());
        assertNotEquals(oldHash, saved.getValue().getDataHash());
    }

    // ── Cache invalidation: model changed ────────────────────────────────────

    @Test
    void generateInsight_modelChanged_treatsAsCacheMiss() throws Exception {
        // Entry exists for same data hash but was generated by a different model
        AiAnalysisCache staleEntry = cachedEntry(1L, DATA_HASH, "old-model:7b", validPayload());
        when(cacheRepository.findByStudentIdAndDataHash(1L, DATA_HASH)).thenReturn(Optional.of(staleEntry));
        mockAiReturning(validPayload());

        StudentInsightsResponse response = insightService.generateInsight(1L);

        // Must be a miss (different model)
        assertFalse(response.fromCache());
        assertEquals(MODEL, response.modelUsed());

        // The existing entry should be updated (reuse same entity, save replaces)
        ArgumentCaptor<AiAnalysisCache> saved = ArgumentCaptor.forClass(AiAnalysisCache.class);
        verify(cacheRepository).save(saved.capture());
        assertEquals(MODEL, saved.getValue().getModelUsed());
    }

    // ── Metadata accuracy ─────────────────────────────────────────────────────

    @Test
    void generateInsight_cacheMiss_metadataAccurate() {
        mockAiReturning(validPayload());

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertEquals(1L, response.studentId());
        assertFalse(response.fromCache());
        assertEquals(MODEL, response.modelUsed());
        assertEquals(DATA_HASH, response.dataHash());
        assertNotNull(response.analyzedAt());
    }

    @Test
    void generateInsight_cacheHit_metadataFromCacheEntry() throws Exception {
        AiAnalysisCache entry = cachedEntry(1L, DATA_HASH, MODEL, validPayload());
        when(cacheRepository.findByStudentIdAndDataHash(1L, DATA_HASH)).thenReturn(Optional.of(entry));

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertTrue(response.fromCache());
        assertEquals(MODEL, response.modelUsed());
        assertEquals(DATA_HASH, response.dataHash());
    }

    // ── Provider failure paths (unchanged) ───────────────────────────────────

    @Test
    void generateInsight_providerUnreachable_throwsAiProviderUnavailableException() {
        when(chatClient.prompt()
                .system(anyString())
                .messages(any(UserMessage.class))
                .call()
                .content())
            .thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(AiProviderUnavailableException.class, () -> insightService.generateInsight(1L));
    }

    @Test
    void generateInsight_genericProviderError_throwsAiAnalysisException() {
        when(chatClient.prompt()
                .system(anyString())
                .messages(any(UserMessage.class))
                .call()
                .content())
            .thenThrow(new RuntimeException("Unexpected model error"));

        assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
    }

    // ── qwen3 reasoning-wrapper sanitization (cache-miss reliability) ─────────

    @Test
    void generateInsight_stripsThinkBlockAndCodeFence_succeeds() {
        String wrapped = "<think>Let me reason about the trajectory step by step...</think>\n"
                + "```json\n" + serialize(validPayload()) + "\n```";
        mockRawContent(wrapped);

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertFalse(response.fromCache());
        assertNotNull(response.insight());
        verify(cacheRepository).save(any());
    }

    @Test
    void generateInsight_stripsPlainReasoningPreamble_succeeds() {
        String wrapped = "Here is the structured assessment you requested:\n" + serialize(validPayload());
        mockRawContent(wrapped);

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertFalse(response.fromCache());
        verify(cacheRepository).save(any());
    }

    @Test
    void generateInsight_unparseableContent_throwsAiAnalysisException() {
        mockRawContent("<think>the model rambled and never emitted any JSON object</think>");
        assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
    }

    @Test
    void generateInsight_nullPayload_throwsAiAnalysisException() {
        mockAiReturning(null);
        assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
    }

    // ── Validation failure paths (unchanged) ─────────────────────────────────

    @Test
    void generateInsight_oversizedPatternsList_throwsAiAnalysisException() {
        StudentInsightPayload bad = new StudentInsightPayload(
                "Assessment", PerformanceProfile.SOLID,
                List.of(pattern(), pattern(), pattern(), pattern(), pattern()),
                Collections.emptyList(), Collections.emptyList(), false, ConfidenceLevel.MEDIUM);
        mockAiReturning(bad);

        AiAnalysisException ex = assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
        assertTrue(ex.getMessage().contains("patterns"));
    }

    @Test
    void generateInsight_nullRequiredFields_throwsAiAnalysisException() {
        StudentInsightPayload bad = new StudentInsightPayload(
                "Assessment", null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false, null);
        mockAiReturning(bad);

        assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
    }

    @Test
    void generateInsight_oversizedAssessment_throwsAiAnalysisException() {
        StudentInsightPayload bad = new StudentInsightPayload(
                "x".repeat(601), PerformanceProfile.SOLID,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false, ConfidenceLevel.MEDIUM);
        mockAiReturning(bad);

        assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
    }

    @Test
    void generateInsight_blankAssessment_throwsAiAnalysisException() {
        StudentInsightPayload bad = new StudentInsightPayload(
                "   ", PerformanceProfile.SOLID,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false, ConfidenceLevel.MEDIUM);
        mockAiReturning(bad);

        assertThrows(AiAnalysisException.class, () -> insightService.generateInsight(1L));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockAiReturning(StudentInsightPayload payload) {
        // The service now consumes the raw completion string and converts it itself,
        // so the mock returns serialized JSON (or null) rather than a ready payload.
        mockRawContent(payload == null ? null : serialize(payload));
    }

    private void mockRawContent(String content) {
        when(chatClient.prompt()
                .system(anyString())
                .messages(any(UserMessage.class))
                .call()
                .content())
            .thenReturn(content);
    }

    private String serialize(StudentInsightPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AiAnalysisCache cachedEntry(Long studentId, String hash, String model,
                                         StudentInsightPayload payload) throws Exception {
        AiAnalysisCache entry = new AiAnalysisCache();
        entry.setStudentId(studentId);
        entry.setDataHash(hash);
        entry.setModelUsed(model);
        entry.setPayload(objectMapper.writeValueAsString(payload));
        entry.setUpdatedAt(Instant.now());
        return entry;
    }

    private StudentInsightPayload validPayload() {
        return new StudentInsightPayload(
                "The student shows consistent improvement across all subjects.",
                PerformanceProfile.SOLID,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false, ConfidenceLevel.MEDIUM);
    }

    private com.internship.student_exam_api.dto.response.PerformancePattern pattern() {
        return new com.internship.student_exam_api.dto.response.PerformancePattern(
                com.internship.student_exam_api.enums.PerformanceTrend.IMPROVING,
                com.internship.student_exam_api.enums.TrendScope.CROSS_SUBJECT,
                "Some description", Collections.emptyList());
    }
}
