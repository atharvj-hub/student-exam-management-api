package com.internship.student_exam_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════
 * ENTITY LAYER — Student
 * ═══════════════════════════════════════════════════════════════
 *
 * @Entity  →  Hibernate: "This Java class represents a DB table."
 *             Hibernate registers this in its SessionFactory.
 *             Without it: Hibernate ignores this class entirely.
 *
 * @Table   →  Maps to the "students" table.
 *             Without it: Hibernate defaults to class name "Student"
 *             (case-sensitive, which causes issues in PostgreSQL).
 *
 * What Hibernate does at startup:
 *   1. Reads all @Entity classes
 *   2. For each one, builds an internal EntityType mapping
 *   3. Flyway (our migration tool) handles actual table creation
 *      (ddl-auto=validate — Hibernate just validates schema matches)
 *
 * ENTITY LIFECYCLE (State Machine):
 *
 *   new Student()         → TRANSIENT  (Hibernate doesn't know it)
 *   repository.save()     → MANAGED    (inside a Session, auto-tracked)
 *   session ends          → DETACHED   (changes no longer auto-synced)
 *   repository.delete()   → REMOVED    (scheduled for DELETE on commit)
 *
 * MANAGED state = Hibernate watches every field change.
 * If you're inside @Transactional and change existing.setName("x"),
 * Hibernate detects the "dirty" field and auto-runs UPDATE on commit.
 * This is called DIRTY CHECKING — no manual save() needed for updates.
 */
@Entity
@Table(
    name = "students",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "email", name = "uk_student_email"),
        @UniqueConstraint(columnNames = "roll_number", name = "uk_student_roll_number")
    }
)
@Getter
@Setter
@NoArgsConstructor  // Required by JPA! Hibernate uses reflection + no-arg constructor
                    // to instantiate objects when loading rows from DB.
                    // Remove this → InstantiationException at runtime on every SELECT.
public class Student {

    /**
     * @Id → This is the primary key. Every @Entity MUST have one.
     *       Without @Id → AnnotationException at startup, app won't start.
     *
     * @GeneratedValue(IDENTITY) → DB auto-increments the ID.
     *   When you do INSERT, you don't provide id — DB assigns it.
     *   Hibernate reads the generated value back and sets it on the object.
     *   After save(): student.getId() will be non-null.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @Column(nullable = false) → Adds NOT NULL at DB level.
     *   But this alone doesn't validate BEFORE hitting the DB.
     *   API validation is handled by StudentCreateRequest/StudentUpdateRequest before service logic runs.
     *   The entity keeps database integrity mapping only.
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "roll_number", nullable = false, unique = true, length = 20)
    private String rollNumber;

    @Column(name = "section", length = 10)
    private String section;

    /**
     * @CreationTimestamp → Hibernate automatically sets this to current time
     *   when the entity is first persisted. You never set this manually.
     *
     * updatable = false → prevents this field from being overwritten on UPDATE.
     *   Even if someone sends a JSON body with createdAt, Hibernate ignores it.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * @UpdateTimestamp → Hibernate automatically updates this on every UPDATE.
     *   Useful for auditing: "when was this record last modified?"
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Convenience constructor (NOT used by Hibernate — it uses the no-arg one)
    public Student(String name, String email, String rollNumber) {
        this.name = name;
        this.email = email;
        this.rollNumber = rollNumber;
    }

    public Student(String name, String email, String rollNumber, String section) {
        this.name = name;
        this.email = email;
        this.rollNumber = rollNumber;
        this.section = section;
    }
}
