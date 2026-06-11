package com.internship.student_exam_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.student_exam_api.dto.response.StudentInsightPayload;
import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.entity.AiAnalysisCache;
import com.internship.student_exam_api.enums.AnalysisType;
import com.internship.student_exam_api.exception.AiAnalysisException;
import com.internship.student_exam_api.exception.AiProviderUnavailableException;
import com.internship.student_exam_api.repository.AiAnalysisCacheRepository;
import com.internship.student_exam_api.service.ai.InsightPromptBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentInsightService {

    private final StudentAnalyticsService analyticsService;
    private final InsightPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final AiAnalysisCacheRepository cacheRepository;

    @Value("${spring.ai.ollama.chat.model:qwen3:8b}")
    private String modelName;

    private static final String SYSTEM_PROMPT = """
        You are an expert academic advisor analyzing student performance.
        Your role is to interpret deterministic statistics and formulate an actionable narrative.

        RULES:
        1. You MUST rely ONLY on the provided data.
        2. Observations must be traceable to specific data rows provided in the user prompt.
        3. State your uncertainty in the confidence field. If the student has taken fewer than 3 exams, confidence MUST be LOW.
        4. Do not hallucinate subject codes; you must only mention subjects that appear in the input data.
        5. Do not re-calculate averages; interpret the ones provided.
        6. Output ONLY the JSON object described below. No reasoning, no preamble,
           no explanation, and no markdown code fences around it.
        /no_think
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        Analyze the following student performance data and return the structured output requested.

        {data_context}

        Based on this data, provide your insights.
        {format_instructions}
        """;

    public StudentInsightsResponse generateInsight(Long studentId) {
        StudentAnalyticsService.RawAnalyticsData rawData = analyticsService.getRawData(studentId);
        String dataContext = promptBuilder.buildUserPromptContext(rawData);
        String dataHash = computeDataHash(dataContext);

        Optional<AiAnalysisCache> cached = cacheRepository.findByStudentIdAndDataHash(studentId, dataHash);
        if (cached.isPresent() && cached.get().getModelUsed().equals(modelName)) {
            AiAnalysisCache entry = cached.get();
            log.debug("Cache hit for student {} hash {}", studentId, dataHash);
            StudentInsightPayload cachedPayload = deserializePayload(entry.getPayload());
            return new StudentInsightsResponse(
                    studentId, AnalysisType.STUDENT_PERFORMANCE,
                    true, entry.getModelUsed(), entry.getUpdatedAt(), dataHash, cachedPayload);
        }

        log.debug("Cache miss for student {} hash {}", studentId, dataHash);

        BeanOutputConverter<StudentInsightPayload> outputConverter = new BeanOutputConverter<>(StudentInsightPayload.class);
        String userPrompt = USER_PROMPT_TEMPLATE
                .replace("{data_context}", dataContext)
                .replace("{format_instructions}", outputConverter.getFormat());

        StudentInsightPayload payload = callAiProvider(outputConverter, userPrompt);
        validatePayload(payload);
        storeCache(cached, studentId, dataHash, payload);

        return new StudentInsightsResponse(
                studentId, AnalysisType.STUDENT_PERFORMANCE,
                false, modelName, Instant.now(), dataHash, payload);
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void storeCache(Optional<AiAnalysisCache> existing, Long studentId,
                             String dataHash, StudentInsightPayload payload) {
        String serialized = serializePayload(payload);
        AiAnalysisCache entry = existing.orElseGet(AiAnalysisCache::new);
        entry.setStudentId(studentId);
        entry.setDataHash(dataHash);
        entry.setModelUsed(modelName);
        entry.setPayload(serialized);
        try {
            cacheRepository.save(entry);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request won the race to insert the same (student_id, data_hash).
            // The correct result is already stored; ignore this.
            log.warn("Cache write race for student {} — ignoring duplicate", studentId);
        }
    }

    private StudentInsightPayload deserializePayload(String json) {
        try {
            return objectMapper.readValue(json, StudentInsightPayload.class);
        } catch (JsonProcessingException e) {
            throw new AiAnalysisException("Cached payload could not be deserialized", e);
        }
    }

    private String serializePayload(StudentInsightPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AiAnalysisException("AI payload could not be serialized for caching", e);
        }
    }

    // ── Hash ──────────────────────────────────────────────────────────────────

    static String computeDataHash(String dataContext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(dataContext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    // ── AI call + validation ──────────────────────────────────────────────────

    private StudentInsightPayload callAiProvider(
            BeanOutputConverter<StudentInsightPayload> outputConverter,
            String userPrompt) {
        String raw;
        try {
            // Take the RAW string rather than .entity(converter): qwen3 prefixes its
            // answer with a <think>…</think> reasoning block (and sometimes a markdown
            // fence), which BeanOutputConverter cannot strip, so it parses the prose as
            // the root value and yields an all-null payload. We sanitize first, then convert.
            raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .messages(new UserMessage(userPrompt))
                    .call()
                    .content();
        } catch (ResourceAccessException e) {
            // Includes socket read-timeout (see AiConfig) — treat a hung/slow model
            // as provider-unavailable rather than letting the thread block forever.
            log.error("AI provider unreachable or timed out: {}", e.getMessage());
            throw new AiProviderUnavailableException("AI provider is currently unreachable", e);
        } catch (AiProviderUnavailableException | AiAnalysisException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI call failed: {}", e.getMessage(), e);
            throw new AiAnalysisException("AI provider returned an unusable response", e);
        }

        String json = sanitizeModelOutput(raw);
        if (json == null || json.isBlank()) {
            throw new AiAnalysisException("AI provider returned an empty or unparseable response");
        }
        try {
            return outputConverter.convert(json);
        } catch (Exception e) {
            log.warn("AI output could not be parsed into StudentInsightPayload: {}", e.getMessage());
            throw new AiAnalysisException("AI response could not be parsed into the required structure", e);
        }
    }

    /**
     * Normalizes a raw LLM completion into a bare JSON object string.
     *
     * <p>qwen3 (and similar reasoning models) wrap structured answers in a
     * {@code <think>…</think>} block and occasionally a {@code ```json} fence.
     * This strips those, then reduces the remainder to the substring spanning the
     * first {@code '{'} to the last {@code '}'} so any residual prose is discarded.
     * Returns {@code null} for {@code null} input.
     */
    static String sanitizeModelOutput(String raw) {
        if (raw == null) {
            return null;
        }
        // Remove complete reasoning blocks, then any stray/unbalanced think tags.
        String s = raw.replaceAll("(?is)<think>.*?</think>", " ")
                      .replaceAll("(?is)</?think>", " ");
        // Strip markdown code fences (```json … ```).
        s = s.replaceAll("(?is)```(?:json)?", " ");
        // Reduce to the JSON object: first '{' .. last '}'.
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1).trim();
        }
        return s.trim();
    }

    private void validatePayload(StudentInsightPayload payload) {
        if (payload == null) {
            throw new AiAnalysisException("AI provider returned a null response");
        }
        Set<ConstraintViolation<StudentInsightPayload>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            log.warn("AI response failed validation: {}", details);
            throw new AiAnalysisException("AI response failed structured output validation: " + details);
        }
    }
}
