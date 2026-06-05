package com.internship.student_exam_api.entity;

import com.internship.student_exam_api.enums.Grade;
import com.internship.student_exam_api.enums.ResultStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result entity — maps to the "results" table.
 * <p>
 * This is the most complex entity. It links Student + Exam and auto-calculates:
 *   - percentage = (marks / exam.subject.totalMarks) * 100
 *   - grade      = based on percentage
 *   - status     = PASS if percentage >= 40, else FAIL
 * <p>
 * Note: percentage, grade, status are NEVER set by the API caller.
 * They are calculated in ResultService BEFORE saving.
 * The entity just stores the computed values.
 * <p>
 * ═══════════════════════════════════════════════════════════════
 * TWO @ManyToOne Relationships
 * ═══════════════════════════════════════════════════════════════
 * <p>
 * Result → Student (many results per student)
 * Result → Exam    (many results per exam, e.g. all students who took it)
 * <p>
 * DB schema for "results" table:
 *   id | student_id | exam_id | marks | percentage | grade | status | ...
 *    1       3           2       75       75.0        A       PASS
 * <p>
 * ═══════════════════════════════════════════════════════════════
 * @Enumerated(EnumType.STRING) — Always Use STRING, Never ORDINAL
 * ═══════════════════════════════════════════════════════════════
 * <p>
 * ORDINAL stores 0, 1, 2 for enum values.
 * Problem: if you add a new Grade between A_PLUS and A,
 *   all existing rows now point to the wrong grade value.
 *   Your DB is silently corrupted with no error.
 * <p>
 * STRING stores "A_PLUS", "A", "B" etc.
 *   Adding new enum values → only new rows use them.
 *   Existing data is untouched. Safe.
 * <p>
 * ═══════════════════════════════════════════════════════════════
 * @UniqueConstraint on (student_id, exam_id)
 * ═══════════════════════════════════════════════════════════════
 * <p>
 * A student can only have ONE result per exam.
 * Without this constraint, you could insert two results for
 * Student#1 + Exam#1 → data corruption.
 * The DB-level unique constraint prevents this as a hard guarantee.
 */
@Entity
@Table(
    name = "results",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"student_id", "exam_id"},
            name = "uk_student_exam_result"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    /**
     * Marks obtained by the student.
     * DTO validation rejects negative marks before service logic runs.
     * Max marks validation happens in ResultService:
     *   marks <= exam.subject.totalMarks (business rule, not just a constraint)
     */
    @Column(name = "marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal marks;

    /**
     * Auto-calculated fields — set by ResultService, not by API caller.
     * percentage = (marks / totalMarks) * 100
     */
    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    /**
     * @Enumerated(EnumType.STRING) → stores "A_PLUS", "A", "B", "C", "FAIL"
     *   in the DB column (not integer ordinals).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 10)
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ResultStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
