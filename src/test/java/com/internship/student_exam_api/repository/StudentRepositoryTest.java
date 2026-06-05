package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class StudentRepositoryTest {

    @Autowired
    private StudentRepository studentRepository;

    @Test
    void findsStudentByEmailAndRollNumber() {
        Student saved = studentRepository.save(new Student("Test Student", "test@example.com", "ROLL001"));

        assertThat(studentRepository.findByEmail("test@example.com")).contains(saved);
        assertThat(studentRepository.findByRollNumber("ROLL001")).contains(saved);
    }

    @Test
    void checksDuplicateEmailExcludingCurrentStudent() {
        Student first = studentRepository.save(new Student("First Student", "first@example.com", "ROLL001"));
        Student second = studentRepository.save(new Student("Second Student", "second@example.com", "ROLL002"));

        assertThat(studentRepository.existsByEmail("first@example.com")).isTrue();
        assertThat(studentRepository.existsByRollNumber("ROLL001")).isTrue();
        // Verify that the same email on a different student is detected as a conflict
        assertThat(studentRepository.existsByEmailAndIdNot("first@example.com", second.getId())).isTrue();
        // Verify that the same email on the SAME student is NOT flagged as a duplicate (allows self-update)
        assertThat(studentRepository.existsByEmailAndIdNot("first@example.com", first.getId())).isFalse();
    }
}
