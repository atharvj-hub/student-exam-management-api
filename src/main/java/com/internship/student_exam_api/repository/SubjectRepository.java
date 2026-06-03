package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    boolean existsBySubjectCode(String subjectCode);

    boolean existsBySubjectCodeAndIdNot(String subjectCode, Long id);
}
