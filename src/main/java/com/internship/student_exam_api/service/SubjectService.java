package com.internship.student_exam_api.service;

import com.internship.student_exam_api.dto.request.SubjectCreateRequest;
import com.internship.student_exam_api.dto.request.SubjectUpdateRequest;
import com.internship.student_exam_api.dto.response.SubjectResponse;
import com.internship.student_exam_api.entity.Subject;
import com.internship.student_exam_api.exception.DuplicateResourceException;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.repository.SubjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional
    public SubjectResponse createSubject(SubjectCreateRequest request) {
        log.info("Creating subject with code: {}", request.getSubjectCode());

        if (subjectRepository.existsBySubjectCode(request.getSubjectCode())) {
            throw new DuplicateResourceException("Subject", "subjectCode", request.getSubjectCode());
        }

        Subject subject = new Subject(
            request.getSubjectName(),
            request.getSubjectCode(),
            request.getTotalMarks()
        );

        Subject saved = subjectRepository.save(subject);
        log.info("Subject created with id: {}", saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> getAllSubjects() {
        return subjectRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(Long id) {
        return toResponse(findSubjectOrThrow(id));
    }

    @Transactional
    public SubjectResponse updateSubject(Long id, SubjectUpdateRequest request) {
        log.info("Updating subject with id: {}", id);
        Subject subject = findSubjectOrThrow(id);

        if (subjectRepository.existsBySubjectCodeAndIdNot(request.getSubjectCode(), id)) {
            throw new DuplicateResourceException("Subject", "subjectCode", request.getSubjectCode());
        }

        subject.setSubjectName(request.getSubjectName());
        subject.setSubjectCode(request.getSubjectCode());
        subject.setTotalMarks(request.getTotalMarks());

        return toResponse(subjectRepository.save(subject));
    }

    @Transactional
    public void deleteSubject(Long id) {
        log.info("Deleting subject with id: {}", id);
        if (!subjectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Subject", id);
        }
        subjectRepository.deleteById(id);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    Subject findSubjectOrThrow(Long id) {
        return subjectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    SubjectResponse toResponse(Subject subject) {
        return SubjectResponse.builder()
            .id(subject.getId())
            .subjectName(subject.getSubjectName())
            .subjectCode(subject.getSubjectCode())
            .totalMarks(subject.getTotalMarks())
            .createdAt(subject.getCreatedAt())
            .build();
    }
}
