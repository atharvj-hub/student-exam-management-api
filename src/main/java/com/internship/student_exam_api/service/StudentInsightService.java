package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.response.StudentInsightPayload;
import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.enums.AnalysisType;
import com.internship.student_exam_api.service.ai.InsightPromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StudentInsightService {

    private final StudentAnalyticsService analyticsService;
    private final InsightPromptBuilder promptBuilder;
    private final ChatClient chatClient;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.ollama.chat.model:qwen3:8b}")
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

        BeanOutputConverter<StudentInsightPayload> outputConverter = new BeanOutputConverter<>(StudentInsightPayload.class);
        String formatInstructions = outputConverter.getFormat();

        String userPrompt = USER_PROMPT_TEMPLATE
                .replace("{data_context}", dataContext)
                .replace("{format_instructions}", formatInstructions);

        // Pass the user prompt as a pre-built Message: text given to .user(String)
        // is rendered through PromptTemplate (StringTemplate), which chokes on the
        // JSON-schema braces in the format instructions. Messages bypass rendering.
        StudentInsightPayload payload = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(new UserMessage(userPrompt))
                .call()
                .entity(outputConverter);

        return new StudentInsightsResponse(
                studentId,
                AnalysisType.STUDENT_PERFORMANCE,
                false, 
                modelName, 
                Instant.now(),
                null,
                payload
        );
    }
}
