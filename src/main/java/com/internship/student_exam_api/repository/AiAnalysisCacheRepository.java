package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.AiAnalysisCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAnalysisCacheRepository extends JpaRepository<AiAnalysisCache, Long> {

    Optional<AiAnalysisCache> findByStudentIdAndDataHash(Long studentId, String dataHash);
}
