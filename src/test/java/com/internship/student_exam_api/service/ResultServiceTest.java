package com.internship.student_exam_api.service;

import static org.junit.jupiter.api.Assertions.*;
import com.internship.student_exam_api.enums.ResultStatus;
import com.internship.student_exam_api.enums.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultServiceTest {

    @Test
    void shouldReturnAPlusFor95Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(new BigDecimal("95"));

        assertEquals(Grade.A_PLUS, grade);
    }
    @Test
    void shouldReturnAFor75Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(new BigDecimal("75"));

        assertEquals(Grade.A, grade);
    }
    @Test
    void shouldReturnBFor60Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(new BigDecimal("60"));

        assertEquals(Grade.B, grade);
    }
    @Test
    void shouldReturnCFor35Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(new BigDecimal("35"));

        assertEquals(Grade.C, grade);
    }
    @Test
    void shouldReturnFailFor34Point99Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(new BigDecimal("34.99"));

        assertEquals(Grade.FAIL, grade);
    }
    @Test
    void shouldReturnPassFor40Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        ResultStatus status =
                resultService.calculateStatus(new BigDecimal("40"));

        assertEquals(ResultStatus.PASS, status);
    }

    @Test
    void shouldReturnFailFor39Point99Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        ResultStatus status =
                resultService.calculateStatus(new BigDecimal("39.99"));

        assertEquals(ResultStatus.FAIL, status);
    }
    @Test
    void shouldCalculate75Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        BigDecimal percentage =
                resultService.calculatePercentage(new BigDecimal("75"), new BigDecimal("100"));

        assertEquals(new BigDecimal("75.00"), percentage);
    }

    @Test
    void shouldCalculate95Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        BigDecimal percentage =
                resultService.calculatePercentage(new BigDecimal("95"), new BigDecimal("100"));

        assertEquals(new BigDecimal("95.00"), percentage);
    }

    @Test
    void shouldRoundToTwoDecimalPlaces() {

        ResultService resultService =
                new ResultService(null, null, null);

        BigDecimal percentage =
                resultService.calculatePercentage(new BigDecimal("1"), new BigDecimal("3"));

        assertEquals(new BigDecimal("33.33"), percentage);
    }
}