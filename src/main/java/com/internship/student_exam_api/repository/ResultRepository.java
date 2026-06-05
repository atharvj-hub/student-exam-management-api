package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Result;
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
}
