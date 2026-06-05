package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.SubjectCreateRequest;
import com.internship.student_exam_api.dto.request.SubjectUpdateRequest;
import com.internship.student_exam_api.dto.response.SubjectResponse;
import com.internship.student_exam_api.service.SubjectService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@Slf4j
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectCreateRequest request) {
        log.info("POST /api/subjects");
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.createSubject(request));
    }

    @GetMapping
    public ResponseEntity<List<SubjectResponse>> getAllSubjects() {
        log.info("GET /api/subjects");
        return ResponseEntity.ok(subjectService.getAllSubjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getSubjectById(@PathVariable Long id) {
        log.info("GET /api/subjects/{}", id);
        return ResponseEntity.ok(subjectService.getSubjectById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> updateSubject(
            @PathVariable Long id,
            @Valid @RequestBody SubjectUpdateRequest request) {
        log.info("PUT /api/subjects/{}", id);
        return ResponseEntity.ok(subjectService.updateSubject(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        log.info("DELETE /api/subjects/{}", id);
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
