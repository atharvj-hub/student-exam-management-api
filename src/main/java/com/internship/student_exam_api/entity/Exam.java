package com.internship.student_exam_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Exam entity — maps to the "exams" table.
 *
 * An Exam is a specific instance of a Subject being tested on a specific date.
 * Example: "Mid-Term Mathematics Exam on 2024-03-15"
 *
 * ═══════════════════════════════════════════════════════════════
 * @ManyToOne — The Most Important Relationship Annotation
 * ═══════════════════════════════════════════════════════════════
 *
 * WHAT IT MEANS:
 *   Many Exams → One Subject
 *   "Mathematics Mid-Term" references Subject("Mathematics")
 *   "Mathematics Final"    references Subject("Mathematics")
 *   Both exams point to the SAME subject row in DB.
 *
 * HOW HIBERNATE STORES THIS:
 *   In the "exams" table, Hibernate creates a foreign key column "subject_id"
 *   that references the "subjects" table's primary key.
 *
 *   exams table:
 *     id | exam_name          | subject_id | exam_date
 *      1   Mid-Term Math          3          2024-03-15
 *      2   Final Math             3          2024-06-20
 *
 * @JoinColumn(name = "subject_id") → Names the FK column in the "exams" table.
 *   Without this: Hibernate generates a column name like "subject_id" anyway,
 *   but explicitly naming it is a best practice for clarity and migration scripts.
 *
 * FETCH TYPES — Critical for Performance:
 *
 *   FetchType.LAZY (DEFAULT for @ManyToOne in modern Hibernate):
 *     When you load an Exam, Hibernate does NOT immediately load the Subject.
 *     Subject data is loaded only when you call exam.getSubject().
 *     Efficient — avoids loading Subject data when you don't need it.
 *
 *   FetchType.EAGER:
 *     Every time you load an Exam, Hibernate automatically JOINs and loads Subject too.
 *     Convenient but dangerous at scale — loading 1000 exams → 1000 subjects loaded too.
 *
 *   We use LAZY here. The caller explicitly fetches subject when needed.
 *
 * THE LazyInitializationException TRAP:
 *   If you access exam.getSubject() OUTSIDE a @Transactional method,
 *   Hibernate's session is already closed → can't lazily load → EXCEPTION.
 *   Fix: use @Transactional, or use JOIN FETCH in your query, or use DTOs.
 *   We use DTOs (see ResultResponse) to avoid exposing raw entities to controllers.
 */
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exam_name", nullable = false, length = 150)
    private String examName;

    /**
     * The relationship: many Exams belong to one Subject.
     * optional = false → subject_id FK cannot be NULL in DB.
     *   Trying to create an Exam without a Subject → ConstraintViolationException.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Exam(String examName, Subject subject, LocalDate examDate) {
        this.examName = examName;
        this.subject = subject;
        this.examDate = examDate;
    }

    public Long getId() {
        return id;
    }

    public String getExamName() {
        return examName;
    }

    public Subject getSubject() {
        return subject;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
