package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.request.ExamRequest;
import com.internship.student_exam_api.dto.response.ExamResponse;
import com.internship.student_exam_api.entity.Exam;
import com.internship.student_exam_api.entity.Subject;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.repository.ExamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExamService {

    private final ExamRepository examRepository;
    private final SubjectService subjectService;

    /**
     * Constructor injection with TWO dependencies.
     * Spring resolves both at startup from the ApplicationContext.
     *
     * WHY inject SubjectService and not SubjectRepository directly?
     *   SubjectService already has findSubjectOrThrow() which gives us
     *   the proper 404 error if subject doesn't exist.
     *   We reuse that logic instead of duplicating it.
     *   This is the principle of service-to-service dependency.
     *
     *   CAUTION: Don't create circular dependencies.
     *   StudentService → SubjectService is fine.
     *   SubjectService → StudentService → SubjectService = circular = Spring startup crash.
     */
    public ExamService(ExamRepository examRepository, SubjectService subjectService) {
        this.examRepository = examRepository;
        this.subjectService = subjectService;
    }

    @Transactional
    public ExamResponse createExam(ExamRequest request) {
        log.info("Creating exam: {} for subject id: {}", request.getExamName(), request.getSubjectId());

        // Reuse SubjectService to fetch and validate the subject
        Subject subject = subjectService.findSubjectOrThrow(request.getSubjectId());

        Exam exam = new Exam(request.getExamName(), subject, request.getExamDate());
        Exam saved = examRepository.save(exam);

        log.info("Exam created with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getAllExams() {
        // JOIN FETCH prevents N+1: loads subject data in same query
        return examRepository.findAllWithSubject()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getExamById(Long id) {
        Exam exam = examRepository.findByIdWithSubject(id)
            .orElseThrow(() -> new ResourceNotFoundException("Exam", id));
        return toResponse(exam);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Package-private so ResultService can use it.
     * Same pattern as SubjectService.findSubjectOrThrow().
     */
    Exam findExamOrThrow(Long id) {
        return examRepository.findByIdWithSubject(id)
            .orElseThrow(() -> new ResourceNotFoundException("Exam", id));
    }

    /**
     * Exam → ExamResponse mapping.
     * Includes nested SubjectResponse.
     *
     * SAFE because:
     *   - We used JOIN FETCH in the repository query
     *   - Subject is already loaded into memory
     *   - No lazy load triggered here
     */
    ExamResponse toResponse(Exam exam) {
        return ExamResponse.builder()
            .id(exam.getId())
            .examName(exam.getExamName())
            .subject(subjectService.toResponse(exam.getSubject()))
            .examDate(exam.getExamDate())
            .createdAt(exam.getCreatedAt())
            .build();
    }
}
