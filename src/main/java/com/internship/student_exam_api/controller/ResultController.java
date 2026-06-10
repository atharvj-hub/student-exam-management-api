package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.ResultCreateRequest;
import com.internship.student_exam_api.dto.request.ResultUpdateRequest;
import com.internship.student_exam_api.dto.response.PagedResponse;
import com.internship.student_exam_api.dto.response.ResultResponse;
import com.internship.student_exam_api.service.ResultService;
import java.util.List;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.internship.student_exam_api.security.permission.Permission;

/**
 * ResultController — 4 endpoints
 *
 * POST   /api/results                  → Record a result
 * GET    /api/results                  → All results
 * PUT    /api/results/{id}             → Update a result
 * GET    /api/results/student/{id}     → All results for a specific student
 *
 * ROUTE CONFLICT AWARENESS:
 *   GET /api/results/{id}        → could conflict with
 *   GET /api/results/student/{id}
 *
 *   Spring resolves this correctly because "student" is a literal string,
 *   not a variable. Spring prioritizes literal path segments over {variables}.
 *   GET /api/results/5        → matches /{id} with id=5
 *   GET /api/results/student/5 → matches /student/{id} with id=5
 *   No conflict.
 */
@RestController
@RequestMapping("/api/results")
@Slf4j
public class ResultController {

    private final ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @PostMapping
    @RequirePermission(Permission.RESULT_CREATE)
    public ResponseEntity<ResultResponse> createResult(@Valid @RequestBody ResultCreateRequest request) {
        log.info("POST /api/results");
        return ResponseEntity.status(HttpStatus.CREATED).body(resultService.createResult(request));
    }

    @GetMapping
    @RequirePermission(Permission.RESULT_VIEW)
    public ResponseEntity<PagedResponse<ResultResponse>> getAllResults(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("GET /api/results — page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(PagedResponse.from(resultService.getAllResults(pageable)));
    }

    @PutMapping("/{id}")
    @RequirePermission(Permission.RESULT_UPDATE)
    public ResponseEntity<ResultResponse> updateResult(
            @PathVariable Long id,
            @Valid @RequestBody ResultUpdateRequest request) {
        log.info("PUT /api/results/{}", id);
        return ResponseEntity.ok(resultService.updateResult(id, request));
    }

    /**
     * GET /api/results/student/{studentId}
     * Returns all results for a specific student.
     *
     * This specific path (/student/{id}) is registered BEFORE the general /{id}
     * because Spring MVC matches more specific paths first.
     */
    @GetMapping("/student/{studentId}")
    @RequirePermission(Permission.RESULT_VIEW)
    public ResponseEntity<List<ResultResponse>> getResultsByStudent(@PathVariable Long studentId) {
        log.info("GET /api/results/student/{}", studentId);
        return ResponseEntity.ok(resultService.getResultsByStudent(studentId));
    }
}
