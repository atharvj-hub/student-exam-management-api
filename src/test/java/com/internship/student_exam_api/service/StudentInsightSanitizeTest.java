package com.internship.student_exam_api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link StudentInsightService#sanitizeModelOutput(String)},
 * the qwen3 reasoning-wrapper stripper that makes cache-miss insight generation
 * reliable. Kept mock-free (separate from StudentInsightServiceTest) so strict
 * stubbing there is unaffected.
 */
class StudentInsightSanitizeTest {

    @Test
    void removesThinkBlockAndCodeFences() {
        String out = StudentInsightService.sanitizeModelOutput(
                "<think>abc</think> ```json {\"a\":1} ```");
        assertEquals("{\"a\":1}", out);
    }

    @Test
    void extractsObjectFromSurroundingProse() {
        String out = StudentInsightService.sanitizeModelOutput(
                "Sure! {\"a\":1,\"b\":2} hope that helps");
        assertEquals("{\"a\":1,\"b\":2}", out);
    }

    @Test
    void stripsStrayUnbalancedThinkTagThenExtractsJson() {
        String out = StudentInsightService.sanitizeModelOutput(
                "<think> reasoning preamble {\"a\":1}");
        assertEquals("{\"a\":1}", out);
    }

    @Test
    void noJsonObjectReturnsTrimmedRemainder() {
        String out = StudentInsightService.sanitizeModelOutput("<think>only reasoning</think>");
        assertFalse(out.contains("<think>"));
        assertFalse(out.contains("{"));
    }

    @Test
    void nullReturnsNull() {
        assertNull(StudentInsightService.sanitizeModelOutput(null));
    }
}
