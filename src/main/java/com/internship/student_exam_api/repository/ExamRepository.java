package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    /**
     * Find all exams for a given subject.
     * Method name: findBySubjectId → WHERE subject_id = ?
     *
     * IMPORTANT: "SubjectId" refers to the id field of the nested subject object.
     * Spring Data JPA navigates the relationship:
     *   exam.subject.id → translates to a JOIN on subject_id FK column
     */
    List<Exam> findBySubjectId(Long subjectId);

    /**
     * @Query with JPQL — use when derived method names get too complex.
     *
     * JPQL (Java Persistence Query Language) operates on ENTITIES, not tables.
     * "FROM Exam e JOIN FETCH e.subject" — the "JOIN FETCH" eagerly loads
     * the subject within the same query, avoiding the N+1 problem.
     *
     * WITHOUT JOIN FETCH:
     *   findAll() → SELECT * FROM exams (1 query)
     *   Then for each exam, accessing exam.getSubject() fires:
     *   SELECT * FROM subjects WHERE id = ? (N queries)
     *   10 exams → 11 total queries. 100 exams → 101 queries.
     *
     * WITH JOIN FETCH:
     *   SELECT e, s FROM exams e JOIN subjects s ON e.subject_id = s.id
     *   1 query. Always. Regardless of number of exams.
     */
    @Query("SELECT e FROM Exam e JOIN FETCH e.subject")
    List<Exam> findAllWithSubject();

    @Query("SELECT e FROM Exam e JOIN FETCH e.subject WHERE e.id = :id")
    java.util.Optional<Exam> findByIdWithSubject(@Param("id") Long id);
}
