package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.request.StudentCreateRequest;
import com.internship.student_exam_api.dto.request.StudentUpdateRequest;
import com.internship.student_exam_api.dto.response.StudentResponse;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.exception.DuplicateResourceException;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.repository.StudentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════
 * SERVICE LAYER — StudentService
 * ═══════════════════════════════════════════════════════════════
 *
 * Responsibilities:
 *   1. Business logic (uniqueness checks, validation rules)
 *   2. Transaction management (@Transactional)
 *   3. Entity ↔ DTO mapping (Request → Entity, Entity → Response)
 *   4. Delegation to Repository for DB operations
 *
 * @Slf4j (Lombok) generates:
 *   private static final Logger log = LoggerFactory.getLogger(StudentService.class);
 *   Lets you do: log.info("Creating student: {}", name)
 *   In production, you'd see these logs in your monitoring system (Datadog, CloudWatch).
 *
 * WHY is Entity ↔ DTO mapping done in the SERVICE and not Controller or Repository?
 *
 *   Controller's job: HTTP in/out. It doesn't know what an entity IS.
 *   Repository's job: DB read/write. It works with entities only.
 *   Service's job: orchestrate. It knows BOTH the DTO contract and the entity structure.
 *
 *   The Service is the "translation layer" between the HTTP world and the DB world.
 *
 * @Transactional — What it does per method:
 *
 *   CREATE/UPDATE/DELETE → write operations, definitely need a transaction.
 *   READ methods → @Transactional(readOnly = true) is a best practice:
 *     - Tells the DB driver this is a read-only connection
 *     - Hibernate skips dirty-checking (no need to track entity changes)
 *     - Some DBs route read-only transactions to replicas (performance win at scale)
 */
@Service
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;

    // Constructor injection — Spring injects the repository at startup.
    // private final = immutable reference. Nobody can swap it after construction.
    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // ════════════════════════════════════════════════════════════
    // CREATE
    // ════════════════════════════════════════════════════════════

    /**
     * Creates a new student.
     *
     * Flow:
     *   1. Check email uniqueness → throw 409 if duplicate
     *   2. Check rollNumber uniqueness → throw 409 if duplicate
     *   3. Map RequestDTO → Entity
     *   4. Save entity (Hibernate → INSERT INTO students ...)
     *   5. Map saved Entity → ResponseDTO
     *   6. Return ResponseDTO
     *
     * @Transactional: The existsBy check + save are ONE transaction.
     *   If save fails after the check, the whole thing rolls back.
     *   No partial state in DB.
     */
    @Transactional
    public StudentResponse createStudent(StudentCreateRequest request) {
        log.info("Creating student with email: {}", request.getEmail());

        // Business rule: email must be unique
        if (studentRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Student", "email", request.getEmail());
        }

        // Business rule: roll number must be unique
        if (studentRepository.existsByRollNumber(request.getRollNumber())) {
            throw new DuplicateResourceException("Student", "rollNumber", request.getRollNumber());
        }

        // Map DTO → Entity
        Student student = new Student(
            request.getName(),
            request.getEmail(),
            request.getRollNumber(),
            request.getSection()
        );

        // Hibernate INSERT — after this line, student.getId() is populated
        Student saved = studentRepository.save(student);
        log.info("Student created with id: {}", saved.getId());

        return toResponse(saved);
    }

    // ════════════════════════════════════════════════════════════
    // READ ALL
    // ════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<StudentResponse> getAllStudents() {
        log.info("Fetching all students");

        // findAll() → SELECT * FROM students
        // .stream().map(this::toResponse) → maps each Student entity to StudentResponse
        // .collect(Collectors.toList()) → collects the stream back into a List
        return studentRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════
    // READ ONE
    // ════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public StudentResponse getStudentById(Long id) {
        log.info("Fetching student with id: {}", id);
        Student student = findStudentOrThrow(id);
        return toResponse(student);
    }

    // ════════════════════════════════════════════════════════════
    // UPDATE
    // ════════════════════════════════════════════════════════════

    /**
     * Update existing student — only mutable fields (name, email).
     *
     * rollNumber is intentionally NOT updated here because it is a natural
     * enrollment key. Once assigned, it must not change; doing so would break
     * external systems that reference roll numbers as identifiers.
     * To change a rollNumber, the record must be deleted and re-created.
     *
     * DIRTY CHECKING — how updates work without calling save():
     *   1. findStudentOrThrow() fetches the entity → it's now MANAGED
     *   2. We call setters on the MANAGED entity
     *   3. @Transactional commit → Hibernate detects changed fields → runs UPDATE SQL
     *   4. No explicit save() needed (but we call it for clarity — safe to do).
     *
     * WHY check existsByEmailAndIdNot?
     *   Student #1 has email = "atharv@gmail.com"
     *   If they update and keep the same email:
     *     existsByEmail("atharv@gmail.com") → true → throws 409 — WRONG.
     *   existsByEmailAndIdNot("atharv@gmail.com", 1L) → false → correct.
     *   "Does this email exist on ANY student other than the one I'm updating?"
     */
    @Transactional
    public StudentResponse updateStudent(Long id, StudentUpdateRequest request) {
        log.info("Updating student with id: {}", id);

        Student student = findStudentOrThrow(id);

        // Check email uniqueness — but exclude this student themselves
        if (studentRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("Student", "email", request.getEmail());
        }

        // Only mutable fields are updated — rollNumber is immutable post-creation
        student.setName(request.getName());
        student.setEmail(request.getEmail());
        student.setSection(request.getSection());

        // Hibernate dirty checking would auto-save on commit, but explicit save is clearer
        Student updated = studentRepository.save(student);
        log.info("Student updated: {}", updated.getId());

        return toResponse(updated);
    }

    // ════════════════════════════════════════════════════════════
    // DELETE
    // ════════════════════════════════════════════════════════════

    @Transactional
    public void deleteStudent(Long id) {
        log.info("Deleting student with id: {}", id);

        // Verify exists first — throw meaningful 404 instead of silent no-op
        if (!studentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Student", id);
        }

        studentRepository.deleteById(id);
        log.info("Student deleted: {}", id);
    }

    // ════════════════════════════════════════════════════════════
    // INTERNAL HELPERS — private, not part of the public API
    // ════════════════════════════════════════════════════════════

    /**
     * Shared helper used by getStudentById, updateStudent, deleteStudent.
     * DRY principle: don't repeat orElseThrow everywhere.
     *
     * package-private (no modifier) so other services can use it via:
     *   studentService.findStudentOrThrow(id) — if injected.
     * Actually making it package-private is fine; services in same package can use it.
     * If cross-package, make it public and documented as internal.
     */
    Student findStudentOrThrow(Long id) {
        return studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    /**
     * Entity → ResponseDTO mapping.
     * Called "toResponse" to be explicit about direction.
     *
     * @Builder pattern (Lombok):
     *   StudentResponse.builder()   → starts building
     *   .id(student.getId())        → sets each field
     *   .build()                    → creates the immutable object
     *
     * This is cleaner than calling setters on a new object.
     * Builder prevents partially-constructed objects from escaping.
     */
    StudentResponse toResponse(Student student) {
        return StudentResponse.builder()
            .id(student.getId())
            .name(student.getName())
            .email(student.getEmail())
            .rollNumber(student.getRollNumber())
            .section(student.getSection())
            .createdAt(student.getCreatedAt())
            .updatedAt(student.getUpdatedAt())
            .build();
    }
}
