package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.Result;
import com.internship.student_exam_api.repository.projection.SectionCohortAggregateProjection;
import com.internship.student_exam_api.repository.projection.StudentTrajectoryProjection;
import com.internship.student_exam_api.repository.projection.SubjectAggregateProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ResultAnalyticsRepository extends Repository<Result, Long> {

    // Trajectory (Native to enforce explicit subquery ordering, LIMIT 50 removed as requested)
    @Query(value = """
        SELECT * FROM (
            SELECT 
                r.percentage as percentage, 
                r.grade as grade, 
                r.status as status, 
                e.exam_name as examName, 
                e.exam_date as examDate, 
                s.subject_code as subjectCode, 
                s.subject_name as subjectName
            FROM results r
            JOIN exams e ON r.exam_id = e.id
            JOIN subjects s ON e.subject_id = s.id
            WHERE r.student_id = :studentId
            ORDER BY e.exam_date DESC
        ) subq
        ORDER BY subq.examDate ASC
        """, nativeQuery = true)
    List<StudentTrajectoryProjection> findTrajectoryByStudentId(@Param("studentId") Long studentId);

    // Subject Aggregates (Native to ensure AVG over DECIMAL returns numeric exactly)
    @Query(value = """
        SELECT 
            s.subject_code as subjectCode,
            s.subject_name as subjectName,
            CAST(COUNT(r.id) AS bigint) as examCount,
            CAST(AVG(r.percentage) AS numeric) as averagePercentage,
            CAST(MIN(r.percentage) AS numeric) as minPercentage,
            CAST(MAX(r.percentage) AS numeric) as maxPercentage
        FROM results r
        JOIN exams e ON r.exam_id = e.id
        JOIN subjects s ON e.subject_id = s.id
        WHERE r.student_id = :studentId
        GROUP BY s.subject_code, s.subject_name
        """, nativeQuery = true)
    List<SubjectAggregateProjection> findSubjectAggregatesByStudentId(@Param("studentId") Long studentId);

    // Overall Section Average
    @Query(value = """
        SELECT CAST(AVG(r.percentage) AS numeric)
        FROM results r
        JOIN students st ON r.student_id = st.id
        WHERE st.section = :section
        """, nativeQuery = true)
    BigDecimal findOverallSectionAverage(@Param("section") String section);

    // Section Subject Averages
    @Query(value = """
        SELECT 
            s.subject_code as subjectCode,
            CAST(AVG(r.percentage) AS numeric) as averagePercentage
        FROM results r
        JOIN students st ON r.student_id = st.id
        JOIN exams e ON r.exam_id = e.id
        JOIN subjects s ON e.subject_id = s.id
        WHERE st.section = :section
        GROUP BY s.subject_code
        """, nativeQuery = true)
    List<SectionCohortAggregateProjection> findSectionSubjectAggregates(@Param("section") String section);
}
