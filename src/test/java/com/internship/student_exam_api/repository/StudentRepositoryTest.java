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
    void checksDuplicateFieldsExcludingCurrentStudent() {
        Student first = studentRepository.save(new Student("First Student", "first@example.com", "ROLL001"));
        Student second = studentRepository.save(new Student("Second Student", "second@example.com", "ROLL002"));

        assertThat(studentRepository.existsByEmail("first@example.com")).isTrue();
        assertThat(studentRepository.existsByRollNumber("ROLL001")).isTrue();
        assertThat(studentRepository.existsByEmailAndIdNot("first@example.com", second.getId())).isTrue();
        assertThat(studentRepository.existsByEmailAndIdNot("first@example.com", first.getId())).isFalse();
        assertThat(studentRepository.existsByRollNumberAndIdNot("ROLL001", second.getId())).isTrue();
        assertThat(studentRepository.existsByRollNumberAndIdNot("ROLL001", first.getId())).isFalse();
    }
}
