package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.StudentRequest;
import com.internship.student_exam_api.dto.response.StudentResponse;
import com.internship.student_exam_api.service.StudentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════
 * CONTROLLER LAYER — StudentController
 * ═══════════════════════════════════════════════════════════════
 *
 * RULE: Controllers are THIN. No business logic. No if/else on domain rules.
 *   The controller's ONLY job is:
 *     1. Accept HTTP request
 *     2. Validate input (@Valid)
 *     3. Call the service
 *     4. Return an HTTP response with correct status code
 *
 * If you see business logic in a controller, that's a bug in the architecture.
 *
 * @RestController internals:
 *   @Controller    → registers with Spring MVC as a request handler
 *   @ResponseBody  → every return value is serialized to JSON and written
 *                    to the HTTP response body by Jackson
 *   Combined = @RestController
 *
 * @RequestMapping("/api/students"):
 *   All routes in this class are prefixed with /api/students.
 *   @GetMapping("/{id}") → full path = GET /api/students/{id}
 *
 * FULL FLOW for POST /api/students:
 *   1. Tomcat receives TCP bytes on port 8080
 *   2. DispatcherServlet receives the parsed HTTP request
 *   3. HandlerMapping: "which method handles POST /api/students?" → createStudent()
 *   4. @Valid fires: checks @NotBlank, @Email on StudentRequest
 *      If fails → MethodArgumentNotValidException → GlobalExceptionHandler → 422
 *   5. @RequestBody: Jackson reads JSON body → StudentRequest object
 *   6. createStudent(request) called
 *   7. StudentService.createStudent() runs
 *   8. Returns StudentResponse
 *   9. Jackson serializes StudentResponse → JSON
 *   10. HTTP 201 Created returned
 *
 * @Valid vs @Validated:
 *   @Valid    → standard JSR-380 Bean Validation, triggers @NotBlank etc.
 *   @Validated → Spring's version, also supports method-level validation and groups
 *   For our use case, @Valid on @RequestBody is sufficient.
 */
@RestController
@RequestMapping("/api/students")
@Slf4j
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * POST /api/students
     * Creates a new student.
     *
     * @Valid → triggers validation on StudentRequest BEFORE calling the service.
     *   If name is blank → 422 response, service is never called.
     *
     * @RequestBody → Jackson deserializes the JSON body into StudentRequest.
     *   Without @RequestBody → StudentRequest is null → NullPointerException in service.
     *
     * ResponseEntity.status(HttpStatus.CREATED).body(response):
     *   Returns HTTP 201 (not 200) because 201 = "resource was created".
     *   200 = "request succeeded, here's the result" (used for GET/PUT).
     *   201 = "a new resource was successfully created" (used for POST).
     */
    @PostMapping
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody StudentRequest request) {
        log.info("POST /api/students");
        StudentResponse response = studentService.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/students
     * Returns all students.
     * ResponseEntity.ok() = HTTP 200 with body.
     */
    @GetMapping
    public ResponseEntity<List<StudentResponse>> getAllStudents() {
        log.info("GET /api/students");
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    /**
     * GET /api/students/{id}
     *
     * @PathVariable Long id → extracts the {id} from the URL and converts to Long.
     *   GET /api/students/5 → id = 5L
     *   GET /api/students/abc → "abc" can't be converted to Long
     *     → MethodArgumentTypeMismatchException → GlobalExceptionHandler → 500
     *     (add handler for MethodArgumentTypeMismatchException → 400 if needed)
     */
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> getStudentById(@PathVariable Long id) {
        log.info("GET /api/students/{}", id);
        return ResponseEntity.ok(studentService.getStudentById(id));
    }

    /**
     * PUT /api/students/{id}
     * Full update — all fields replaced.
     * Returns 200 (not 201) because the resource already existed.
     */
    @PutMapping("/{id}")
    public ResponseEntity<StudentResponse> updateStudent(
            @PathVariable Long id,
            @Valid @RequestBody StudentRequest request) {
        log.info("PUT /api/students/{}", id);
        return ResponseEntity.ok(studentService.updateStudent(id, request));
    }

    /**
     * DELETE /api/students/{id}
     *
     * HTTP 204 No Content:
     *   204 = "request succeeded, nothing to return"
     *   Correct status for DELETE — the resource is gone, there's nothing to return.
     *   Using 200 for DELETE is technically wrong (200 implies a body).
     *
     * ResponseEntity<Void>: no body type.
     * .build() instead of .body() because there's no body.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        log.info("DELETE /api/students/{}", id);
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}
