package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.response.StudentInsightPayload;
import com.internship.student_exam_api.dto.response.StudentInsightsResponse;
import com.internship.student_exam_api.service.ai.InsightPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Answers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentInsightServiceTest {

    @Mock
    private StudentAnalyticsService analyticsService;

    @Mock
    private InsightPromptBuilder promptBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @InjectMocks
    private StudentInsightService insightService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(insightService, "modelName", "qwen3:8b");
    }

    @Test
    void generateInsight_Success() {
        // Mock data retrieval
        StudentAnalyticsService.RawAnalyticsData rawData = new StudentAnalyticsService.RawAnalyticsData(null, Collections.emptyList());
        when(analyticsService.getRawData(1L)).thenReturn(rawData);
        when(promptBuilder.buildUserPromptContext(rawData)).thenReturn("Mock Context");

        // Mock the entity return using deep stubs
        StudentInsightPayload mockPayload = new StudentInsightPayload("Assessment", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, null);
        when(chatClient.prompt()
                .system(anyString())
                .messages(any(UserMessage.class))
                .call()
                .entity(ArgumentMatchers.<org.springframework.ai.converter.BeanOutputConverter<StudentInsightPayload>>any()))
            .thenReturn(mockPayload);

        StudentInsightsResponse response = insightService.generateInsight(1L);

        assertNotNull(response);
        assertNotNull(response.insight());
    }
}
