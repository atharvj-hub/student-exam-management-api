package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ═══════════════════════════════════════════════════════════════
 * REPOSITORY LAYER — StudentRepository
 * ═══════════════════════════════════════════════════════════════
 *
 * extends JpaRepository<Student, Long> gives you for FREE:
 *   save(entity)       → INSERT or UPDATE (merge if id != null)
 *   findById(id)       → SELECT WHERE id = ? → Optional<Student>
 *   findAll()          → SELECT * FROM students
 *   existsById(id)     → SELECT COUNT(*) WHERE id = ?
 *   deleteById(id)     → DELETE WHERE id = ?
 *   count()            → SELECT COUNT(*)
 *   saveAll(list)      → batch INSERT/UPDATE
 *
 * HOW Spring generates the implementation:
 *   At startup, Spring Data JPA creates a PROXY CLASS that:
 *   1. Implements all JpaRepository methods using EntityManager internally
 *   2. Parses method names like findByEmail → generates SQL
 *   3. Registers the proxy as a Bean in ApplicationContext
 *
 * @Repository:
 *   1. Marks this as a Spring Bean (picked up by @ComponentScan)
 *   2. Enables Spring's persistence exception translation:
 *      Raw JPA exceptions → Spring's DataAccessException hierarchy
 *      (cleaner, framework-agnostic exceptions for the service layer)
 *
 * RULE: Repository = data access only. No business logic. No if/else for domain rules.
 * If you need to validate uniqueness, call existsByEmail() from the SERVICE.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    /**
     * Method name → SQL translation:
     *   findByEmail(email) → SELECT * FROM students WHERE email = ?
     *
     * Returns Optional<Student> because the student might not exist.
     * Never return null — Optional forces the caller to handle the "not found" case.
     */
    Optional<Student> findByEmail(String email);

    Optional<Student> findByRollNumber(String rollNumber);

    /**
     * existsByEmail → SELECT COUNT(*) FROM students WHERE email = ?
     * Returns boolean — more efficient than findByEmail when you only need existence.
     * Used in service to check duplicates BEFORE attempting INSERT.
     */
    boolean existsByEmail(String email);

    boolean existsByRollNumber(String rollNumber);

    /**
     * For update validation: check if another student (not the one being updated)
     * already has this email.
     *
     *   existsByEmailAndIdNot(email, id)
     *   → SELECT COUNT(*) FROM students WHERE email = ? AND id != ?
     *
     * This prevents false duplicate detection when updating a student's own email.
     * Without this: updating student#1 with their SAME email would trigger duplicate error.
     *
     * Note: existsByRollNumberAndIdNot has been intentionally removed because rollNumber
     * is now immutable after creation and cannot be changed via the update endpoint.
     */
    boolean existsByEmailAndIdNot(String email, Long id);
}
