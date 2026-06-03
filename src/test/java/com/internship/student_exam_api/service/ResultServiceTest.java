package com.internship.student_exam_api.service;

import static org.junit.jupiter.api.Assertions.*;
import com.internship.student_exam_api.enums.ResultStatus;
import com.internship.student_exam_api.enums.Grade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultServiceTest {

    @Test
    void shouldReturnAPlusFor95Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(95.0);

        assertEquals(Grade.A_PLUS, grade);
    }
    @Test
    void shouldReturnAFor75Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(75.0);

        assertEquals(Grade.A, grade);
    }
    @Test
    void shouldReturnBFor60Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(60.0);

        assertEquals(Grade.B, grade);
    }
    @Test
    void shouldReturnCFor35Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(35.0);

        assertEquals(Grade.C, grade);
    }
    @Test
    void shouldReturnFailFor34Point99Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        Grade grade = resultService.calculateGrade(34.99);

        assertEquals(Grade.FAIL, grade);
    }
    @Test
    void shouldReturnPassFor40Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        ResultStatus status =
                resultService.calculateStatus(40.0);

        assertEquals(ResultStatus.PASS, status);
    }

    @Test
    void shouldReturnFailFor39Point99Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        ResultStatus status =
                resultService.calculateStatus(39.99);

        assertEquals(ResultStatus.FAIL, status);
    }
    @Test
    void shouldCalculate75Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        double percentage =
                resultService.calculatePercentage(75, 100);

        assertEquals(75.0, percentage);
    }

    @Test
    void shouldCalculate95Percent() {

        ResultService resultService =
                new ResultService(null, null, null);

        double percentage =
                resultService.calculatePercentage(95, 100);

        assertEquals(95.0, percentage);
    }

    @Test
    void shouldRoundToTwoDecimalPlaces() {

        ResultService resultService =
                new ResultService(null, null, null);

        double percentage =
                resultService.calculatePercentage(1, 3);

        assertEquals(33.33, percentage);
    }
}