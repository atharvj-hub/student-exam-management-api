package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.ExamRequest;
import com.internship.student_exam_api.dto.response.ExamResponse;
import com.internship.student_exam_api.service.ExamService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
@Slf4j
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamRequest request) {
        log.info("POST /api/exams");
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.createExam(request));
    }

    @GetMapping
    public ResponseEntity<List<ExamResponse>> getAllExams() {
        log.info("GET /api/exams");
        return ResponseEntity.ok(examService.getAllExams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamResponse> getExamById(@PathVariable Long id) {
        log.info("GET /api/exams/{}", id);
        return ResponseEntity.ok(examService.getExamById(id));
    }
}
