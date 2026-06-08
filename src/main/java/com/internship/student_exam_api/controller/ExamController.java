package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.ExamRequest;
import com.internship.student_exam_api.dto.response.ExamResponse;
import com.internship.student_exam_api.service.ExamService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ExamController — manages exams.
 *
 * <p>SECURITY — DUAL-LAYER DEFENSE:
 * <ul>
 *   <li>Layer 1 (URL): SecurityConfig enforces HTTP-method-level roles globally.</li>
 *   <li>Layer 2 (Method): @PreAuthorize enforces roles at the method AOP proxy level.</li>
 * </ul>
 * Both layers must agree before the request reaches business logic.
 */
@RestController
@RequestMapping("/api/exams")
@Slf4j
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /**
     * POST /api/exams — Create a new exam.
     * Restricted to ADMIN role only.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamRequest request) {
        log.info("POST /api/exams");
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.createExam(request));
    }

    /**
     * GET /api/exams — List all exams.
     * Accessible by ADMIN and STUDENT roles.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<List<ExamResponse>> getAllExams() {
        log.info("GET /api/exams");
        return ResponseEntity.ok(examService.getAllExams());
    }

    /**
     * GET /api/exams/{id} — Get an exam by ID.
     * Accessible by ADMIN and STUDENT roles.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<ExamResponse> getExamById(@PathVariable Long id) {
        log.info("GET /api/exams/{}", id);
        return ResponseEntity.ok(examService.getExamById(id));
    }
}
