package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Result;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════
 * ResultRepository — Most Complex Repository
 * ═══════════════════════════════════════════════════════════════
 *
 * Results have TWO foreign keys: student_id + exam_id.
 * Most queries here navigate BOTH relationships.
 *
 * KEY PATTERN: JOIN FETCH to avoid LazyInitializationException
 *
 * Result has @ManyToOne(LAZY) to both Student and Exam.
 * Exam has @ManyToOne(LAZY) to Subject.
 *
 * Without JOIN FETCH, when service builds ResultResponse:
 *   result.getStudent()        → LAZY load fires → OK if inside @Transactional
 *   result.getExam()           → LAZY load fires → OK
 *   result.getExam().getSubject() → LAZY load fires → OK
 *
 * But if the session closes before the controller serializes... BOOM.
 * The safest approach: JOIN FETCH everything you'll need in the query itself.
 * Then the DTO mapping never needs to trigger lazy loads.
 */
@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

    /**
     * Check if a result already exists for this student + exam combination.
     * Used in ResultService to enforce: one result per student per exam.
     *
     * Generated SQL:
     *   SELECT COUNT(*) > 0 FROM results WHERE student_id = ? AND exam_id = ?
     *
     * Note: existsByStudentIdAndExamIdAndIdNot has been intentionally removed.
     * It was previously needed when ResultUpdateRequest carried studentId/examId,
     * allowing a PUT to re-assign a result to a different student/exam.
     * Now that ResultUpdateRequest only carries marks, the student-exam pair
     * is immutable on update and no such guard is required.
     */
    boolean existsByStudentIdAndExamId(Long studentId, Long examId);

    /**
     * Fetch all results for a student — used in GET /api/results/student/{id}
     *
     * JOIN FETCH loads Student + Exam + Subject in ONE query.
     *
     * Without JOIN FETCH:
     *   findByStudentId(1L) → SELECT * FROM results WHERE student_id = 1 (1 query)
     *   For each result, accessing exam.getSubject() → N more queries
     *   10 results → 21 total queries (1 + 10 for exam + 10 for subject)
     *
     * With JOIN FETCH:
     *   1 query, regardless of result count.
     *
     * JPQL note: "r.exam.subject" navigates the relationship chain.
     * Hibernate translates this to JOINs on the FK columns.
     */
    @Query("SELECT r FROM Result r " +
           "JOIN FETCH r.student " +
           "JOIN FETCH r.exam e " +
           "JOIN FETCH e.subject " +
           "WHERE r.student.id = :studentId")
    List<Result> findByStudentIdWithDetails(@Param("studentId") Long studentId);

    /**
     * Fetch all results with full details — used in GET /api/results
     */
    @Query("SELECT r FROM Result r " +
           "JOIN FETCH r.student " +
           "JOIN FETCH r.exam e " +
           "JOIN FETCH e.subject")
    List<Result> findAllWithDetails();

    /**
     * Fetch one result by ID with full details — used in PUT /api/results/{id}
     */
    @Query("SELECT r FROM Result r " +
           "JOIN FETCH r.student " +
           "JOIN FETCH r.exam e " +
           "JOIN FETCH e.subject " +
           "WHERE r.id = :id")
    Optional<Result> findByIdWithDetails(@Param("id") Long id);

    /**
     * Paginated fetch of all results with full details — used in GET /api/results.
     *
     * WHY a separate countQuery is mandatory:
     *   Spring Data JPA derives the count query by wrapping the value query in
     *   SELECT COUNT(*). Hibernate cannot wrap a JOIN FETCH clause in COUNT,
     *   because JOIN FETCH is a Hibernate-specific hint to eagerly load associations
     *   and has no meaning in a scalar count context.
     *   Without countQuery → IllegalQueryOperationException at startup.
     *
     * WHY no duplicates despite JOIN FETCH:
     *   All joins here are @ManyToOne (Result→Student, Result→Exam, Exam→Subject).
     *   @ManyToOne joins produce exactly one row per Result — no multiplication.
     *   Duplicates only occur with @OneToMany collections, which are absent here.
     *
     * WHY no DISTINCT needed:
     *   Follows from the above — SQL result set has exactly N rows for N results.
     *   Hibernate applies LIMIT/OFFSET at the database level correctly.
     */
    @Query(
        value      = "SELECT r FROM Result r " +
                     "JOIN FETCH r.student " +
                     "JOIN FETCH r.exam e " +
                     "JOIN FETCH e.subject",
        countQuery = "SELECT COUNT(r) FROM Result r"
    )
    Page<Result> findAllWithDetailsPageable(Pageable pageable);
}

