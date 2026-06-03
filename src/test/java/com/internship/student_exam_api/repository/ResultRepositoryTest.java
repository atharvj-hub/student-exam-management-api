package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Exam;
import com.internship.student_exam_api.entity.Result;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.entity.Subject;
import com.internship.student_exam_api.enums.Grade;
import com.internship.student_exam_api.enums.ResultStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

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
        persistResult(firstStudent, exam, 90.0, Grade.A_PLUS);
        persistResult(secondStudent, exam, 70.0, Grade.B);
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
        Result saved = persistResult(student, exam, 75.0, Grade.A);
        entityManager.flush();
        entityManager.clear();

        Result result = resultRepository.findByIdWithDetails(saved.getId()).orElseThrow();

        assertThat(result.getStudent().getRollNumber()).isEqualTo("ROLL001");
        assertThat(result.getExam().getExamName()).isEqualTo("Final Exam");
        assertThat(result.getExam().getSubject().getSubjectName()).isEqualTo("Mathematics");
    }

    @Test
    void existenceChecksSupportCreateAndUpdateDuplicateRules() {
        Student student = persistStudent("Test Student", "test@example.com", "ROLL001");
        Exam exam = persistExam();
        Result result = persistResult(student, exam, 82.0, Grade.A);
        entityManager.flush();

        assertThat(resultRepository.existsByStudentIdAndExamId(student.getId(), exam.getId())).isTrue();
        assertThat(resultRepository.existsByStudentIdAndExamIdAndIdNot(student.getId(), exam.getId(), result.getId())).isFalse();
        assertThat(resultRepository.existsByStudentIdAndExamIdAndIdNot(student.getId(), exam.getId(), 999L)).isTrue();
    }

    private Student persistStudent(String name, String email, String rollNumber) {
        return entityManager.persist(new Student(name, email, rollNumber));
    }

    private Exam persistExam() {
        Subject subject = entityManager.persist(new Subject("Mathematics", "MATH101", 100));
        return entityManager.persist(new Exam("Final Exam", subject, LocalDate.of(2026, 6, 3)));
    }

    private Result persistResult(Student student, Exam exam, Double marks, Grade grade) {
        Result result = new Result();
        result.setStudent(student);
        result.setExam(exam);
        result.setMarks(marks);
        result.setPercentage(marks);
        result.setGrade(grade);
        result.setStatus(ResultStatus.PASS);
        return entityManager.persist(result);
    }
}
