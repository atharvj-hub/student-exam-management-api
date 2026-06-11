package com.internship.student_exam_api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the deterministic SHA-256 hash used as the analytics cache key. */
class DataHashTest {

    @Test
    void sameInput_producesIdenticalHash() {
        String h1 = StudentInsightService.computeDataHash("identical input");
        String h2 = StudentInsightService.computeDataHash("identical input");
        assertEquals(h1, h2);
    }

    @Test
    void differentInput_producesDifferentHash() {
        String h1 = StudentInsightService.computeDataHash("input A");
        String h2 = StudentInsightService.computeDataHash("input B");
        assertNotEquals(h1, h2);
    }

    @Test
    void output_is64LowercaseHexChars() {
        String hash = StudentInsightService.computeDataHash("any content");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
}
