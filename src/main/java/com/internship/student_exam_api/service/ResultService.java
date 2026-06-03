package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.request.ResultRequest;
import com.internship.student_exam_api.dto.response.ResultResponse;
import com.internship.student_exam_api.entity.Exam;
import com.internship.student_exam_api.entity.Result;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.enums.Grade;
import com.internship.student_exam_api.enums.ResultStatus;
import com.internship.student_exam_api.exception.BusinessRuleException;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.repository.ResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════
 * ResultService — Business Logic Hub
 * ═══════════════════════════════════════════════════════════════
 *
 * This service implements ALL business rules from the BRD:
 *   1. percentage = (marks / totalMarks) * 100
 *   2. grade:  A+ ≥ 90, A ≥ 75, B ≥ 60, C ≥ 35, FAIL < 35
 *   3. status: PASS if percentage ≥ 40, FAIL otherwise
 *   4. marks cannot exceed totalMarks
 *   5. one result per student per exam
 *
 * All these rules live HERE, in the service.
 * Not in the Controller (HTTP layer).
 * Not in the Repository (data access layer).
 * Not in the Entity (data model).
 *
 * WHY NOT in the Entity?
 *   Some people put calculateGrade() on the Result entity.
 *   Problem: entities should be simple data holders.
 *   Business logic in entities creates tight coupling between
 *   the data model and the business rules.
 *   If the grade boundaries change, you change the entity AND
 *   all tests that load entities. Services are easier to test in isolation.
 */
@Service
@Slf4j
public class ResultService {

    private final ResultRepository resultRepository;
    private final StudentService studentService;
    private final ExamService examService;

    public ResultService(ResultRepository resultRepository,
                         StudentService studentService,
                         ExamService examService) {
        this.resultRepository = resultRepository;
        this.studentService = studentService;
        this.examService = examService;
    }

    // ════════════════════════════════════════════════════════════
    // CREATE — Record a Result
    // ════════════════════════════════════════════════════════════

    @Transactional
    public ResultResponse createResult(ResultRequest request) {
        log.info("Recording result for studentId: {}, examId: {}",
            request.getStudentId(), request.getExamId());

        // 1. Validate student exists
        Student student = studentService.findStudentOrThrow(request.getStudentId());

        // 2. Validate exam exists (with subject loaded via JOIN FETCH)
        Exam exam = examService.findExamOrThrow(request.getExamId());

        // 3. Business rule: one result per student per exam
        if (resultRepository.existsByStudentIdAndExamId(request.getStudentId(), request.getExamId())) {
            throw new BusinessRuleException(
                "Result already recorded for student id: " + request.getStudentId() +
                " and exam id: " + request.getExamId()
            );
        }

        // 4. Business rule: marks cannot exceed totalMarks
        double totalMarks = exam.getSubject().getTotalMarks();
        if (request.getMarks() > totalMarks) {
            throw new BusinessRuleException(
                "Marks (" + request.getMarks() + ") cannot exceed total marks (" + totalMarks + ")"
            );
        }

        // 5. Auto-calculate percentage, grade, status
        double percentage = calculatePercentage(request.getMarks(), totalMarks);
        Grade grade = calculateGrade(percentage);
        ResultStatus status = calculateStatus(percentage);

        log.info("Calculated: percentage={}, grade={}, status={}", percentage, grade, status);

        // 6. Build and save the Result entity
        Result result = new Result();
        result.setStudent(student);
        result.setExam(exam);
        result.setMarks(request.getMarks());
        result.setPercentage(percentage);
        result.setGrade(grade);
        result.setStatus(status);

        Result saved = resultRepository.save(result);
        log.info("Result saved with id: {}", saved.getId());

        return toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════
    // READ ALL
    // ════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ResultResponse> getAllResults() {
        return resultRepository.findAllWithDetails()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // READ BY STUDENT
    // ════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ResultResponse> getResultsByStudent(Long studentId) {
        log.info("Fetching results for studentId: {}", studentId);

        // Validate student exists first
        studentService.findStudentOrThrow(studentId);

        return resultRepository.findByStudentIdWithDetails(studentId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // UPDATE — Re-record marks (recalculates grade/status)
    // ════════════════════════════════════════════════════════════

    @Transactional
    public ResultResponse updateResult(Long id, ResultRequest request) {
        log.info("Updating result with id: {}", id);

        Result result = resultRepository.findByIdWithDetails(id)
            .orElseThrow(() -> new ResourceNotFoundException("Result", id));

        // Validate new student/exam references
        Student student = studentService.findStudentOrThrow(request.getStudentId());
        Exam exam = examService.findExamOrThrow(request.getExamId());

        // Check if new student+exam combination is taken by a DIFFERENT result
        if (resultRepository.existsByStudentIdAndExamIdAndIdNot(
                request.getStudentId(), request.getExamId(), id)) {
            throw new BusinessRuleException(
                "Another result already exists for student id: " + request.getStudentId() +
                " and exam id: " + request.getExamId()
            );
        }

        double totalMarks = exam.getSubject().getTotalMarks();
        if (request.getMarks() > totalMarks) {
            throw new BusinessRuleException(
                "Marks (" + request.getMarks() + ") cannot exceed total marks (" + totalMarks + ")"
            );
        }

        // Recalculate everything
        double percentage = calculatePercentage(request.getMarks(), totalMarks);
        Grade grade = calculateGrade(percentage);
        ResultStatus status = calculateStatus(percentage);

        result.setStudent(student);
        result.setExam(exam);
        result.setMarks(request.getMarks());
        result.setPercentage(percentage);
        result.setGrade(grade);
        result.setStatus(status);

        return toResponse(resultRepository.save(result));
    }

    // ════════════════════════════════════════════════════════════
    // BUSINESS LOGIC — Pure calculation methods (easy to unit test)
    // ════════════════════════════════════════════════════════════

    /**
     * percentage = (marks / totalMarks) * 100
     * Rounded to 2 decimal places.
     *
     * WHY Math.round(... * 100.0) / 100.0?
     *   (75.0 / 100.0) * 100 = 75.000000000001 (floating point imprecision)
     *   We round to 2 decimal places for clean output.
     *
     * EDGE CASE: totalMarks = 0 would cause division by zero.
     *   Prevented at entity level: @Min(1) on Subject.totalMarks.
     *   Defense-in-depth: also caught by DB constraint.
     */
    double calculatePercentage(double marks, double totalMarks) {
        double raw = (marks / totalMarks) * 100.0;
        return Math.round(raw * 100.0) / 100.0;
    }

    /**
     * BRD Grade Rules:
     *   A+   → 90%+
     *   A    → 75% - 89%
     *   B    → 60% - 74%
     *   C    → 35% - 59%
     *   FAIL → below 35%
     *
     * Implementation: if-else chain from highest to lowest.
     * Enum return type means no invalid grade can ever be returned.
     * If you add a new grade tier, the compiler forces you to handle it.
     */
    Grade calculateGrade(double percentage) {
        if (percentage >= 90.0) return Grade.A_PLUS;
        if (percentage >= 75.0) return Grade.A;
        if (percentage >= 60.0) return Grade.B;
        if (percentage >= 35.0) return Grade.C;
        return Grade.FAIL;
    }

    /**
     * BRD Status Rule:
     *   PASS → percentage >= 40%
     *   FAIL → percentage < 40%
     *
     * NOTE: Grade and Status are independent calculations.
     *   A student can have Grade.C (35-59%) and PASS (if >= 40%)
     *   A student can have Grade.C (35-39%) and FAIL (if < 40%)
     *   A student can have Grade.FAIL (<35%) and FAIL (always)
     */
    ResultStatus calculateStatus(double percentage) {
        return percentage >= 40.0 ? ResultStatus.PASS : ResultStatus.FAIL;
    }

    // ════════════════════════════════════════════════════════════
    // MAPPING — Entity → Response DTO
    // ════════════════════════════════════════════════════════════

    private ResultResponse toResponse(Result result) {
        return ResultResponse.builder()
            .id(result.getId())
            .student(studentService.toResponse(result.getStudent()))
            .exam(examService.toResponse(result.getExam()))
            .marks(result.getMarks())
            .percentage(result.getPercentage())
            .grade(result.getGrade())
            .status(result.getStatus())
            .createdAt(result.getCreatedAt())
            .updatedAt(result.getUpdatedAt())
            .build();
    }
}
