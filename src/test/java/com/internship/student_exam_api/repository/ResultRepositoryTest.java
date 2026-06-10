package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Exam;
import com.internship.student_exam_api.entity.Result;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.entity.Subject;
import com.internship.student_exam_api.enums.Grade;
import com.internship.student_exam_api.enums.ResultStatus;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ResultRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ResultRepository resultRepository;

    @Test
    void findByStudentIdWithDetailsReturnsOnlyThatStudentsResults() {
        Student firstStudent = persistStudent("First Student", "first@example.com", "ROLL001");
        Student secondStudent = persistStudent("Second Student", "second@example.com", "ROLL002");
        Exam exam = persistExam();
        persistResult(firstStudent, exam, new BigDecimal("90.00"), Grade.A_PLUS);
        persistResult(secondStudent, exam, new BigDecimal("70.00"), Grade.B);
        entityManager.flush();
        entityManager.clear();

        List<Result> results = resultRepository.findByStudentIdWithDetails(firstStudent.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStudent().getEmail()).isEqualTo("first@example.com");
        assertThat(results.get(0).getExam().getSubject().getSubjectCode()).isEqualTo("MATH101");
    }

    @Test
    void findByIdWithDetailsFetchesStudentExamAndSubject() {
        Student student = persistStudent("Test Student", "test@example.com", "ROLL001");
        Exam exam = persistExam();
        Result saved = persistResult(student, exam, new BigDecimal("75.00"), Grade.A);
        entityManager.flush();
        entityManager.clear();

        Result result = resultRepository.findByIdWithDetails(saved.getId()).orElseThrow();

        assertThat(result.getStudent().getRollNumber()).isEqualTo("ROLL001");
        assertThat(result.getExam().getExamName()).isEqualTo("Final Exam");
        assertThat(result.getExam().getSubject().getSubjectName()).isEqualTo("Mathematics");

        // Guard: assert the full object graph was eagerly loaded by the JOIN FETCH query.
        // If someone removes a JOIN FETCH from findByIdWithDetails(), this will fail before
        // a LazyInitializationException can silently corrupt production data.
        assertThat(Hibernate.isInitialized(result.getStudent()))
            .as("Student must be eagerly initialized by JOIN FETCH")
            .isTrue();
        assertThat(Hibernate.isInitialized(result.getExam()))
            .as("Exam must be eagerly initialized by JOIN FETCH")
            .isTrue();
        assertThat(Hibernate.isInitialized(result.getExam().getSubject()))
            .as("Subject must be eagerly initialized by JOIN FETCH")
            .isTrue();
    }

    @Test
    void existenceCheckSupportsDuplicateResultGuard() {
        Student student = persistStudent("Test Student", "test@example.com", "ROLL001");
        Exam exam = persistExam();
        persistResult(student, exam, new BigDecimal("82.00"), Grade.A);
        entityManager.flush();

        // After saving a result, the student+exam pair must be detected as already existing
        assertThat(resultRepository.existsByStudentIdAndExamId(student.getId(), exam.getId())).isTrue();
        // A different student+exam pair must NOT be detected as existing
        assertThat(resultRepository.existsByStudentIdAndExamId(999L, exam.getId())).isFalse();
    }

    private Student persistStudent(String name, String email, String rollNumber) {
        return entityManager.persist(new Student(name, email, rollNumber));
    }

    private Exam persistExam() {
        Subject subject = entityManager.persist(new Subject("Mathematics", "MATH101", 100));
        return entityManager.persist(new Exam("Final Exam", subject, LocalDate.of(2026, 6, 3)));
    }

    private Result persistResult(Student student, Exam exam, BigDecimal marks, Grade grade) {
        Result result = new Result();
        result.setStudent(student);
        result.setExam(exam);
        result.setMarks(marks.doubleValue());
        result.setPercentage(marks.doubleValue());
        result.setGrade(grade);
        result.setStatus(ResultStatus.PASS);
        return entityManager.persist(result);
    }
}
