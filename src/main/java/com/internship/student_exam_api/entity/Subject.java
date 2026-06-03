package com.internship.student_exam_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Subject entity — maps to the "subjects" table.
 *
 * Represents a subject like "Mathematics", "Physics", etc.
 * totalMarks is used by the Result module to calculate percentage:
 *   percentage = (marks / totalMarks) * 100
 *
 * WHY totalMarks ON Subject AND NOT ON Result?
 *   Because totalMarks is a property of the subject, not of a specific exam result.
 *   "Mathematics always has 100 total marks" — that's a Subject fact.
 *   Storing it on Result would denormalize the data and allow inconsistency.
 */
@Entity
@Table(
    name = "subjects",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "subject_code", name = "uk_subject_code")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    @Column(name = "subject_code", nullable = false, unique = true, length = 20)
    private String subjectCode;

    /**
     * totalMarks must be at least 1.
     *   Prevents division-by-zero in percentage calculation:
     *   percentage = (marks / totalMarks) * 100
     *   If totalMarks = 0 → ArithmeticException (or Infinity in double math)
     */
    @Column(name = "total_marks", nullable = false)
    private Integer totalMarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Subject(String subjectName, String subjectCode, Integer totalMarks) {
        this.subjectName = subjectName;
        this.subjectCode = subjectCode;
        this.totalMarks = totalMarks;
    }
}
