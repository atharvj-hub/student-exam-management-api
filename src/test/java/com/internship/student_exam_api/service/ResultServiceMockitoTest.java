package com.internship.student_exam_api.service;

import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.entity.Subject;
import com.internship.student_exam_api.entity.Exam;
import com.internship.student_exam_api.exception.BusinessRuleException;
import com.internship.student_exam_api.repository.ResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.internship.student_exam_api.dto.request.ResultCreateRequest;
import com.internship.student_exam_api.dto.request.ResultUpdateRequest;
import com.internship.student_exam_api.dto.response.ResultResponse;
import com.internship.student_exam_api.entity.Result;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
@ExtendWith(MockitoExtension.class)
class ResultServiceMockitoTest {

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private StudentService studentService;

    @Mock
    private ExamService examService;

    @InjectMocks
    private ResultService resultService;
    @Test
    void shouldThrowExceptionWhenResultAlreadyExists() {

        ResultCreateRequest request = new ResultCreateRequest();

        request.setStudentId(1L);
        request.setExamId(1L);
        request.setMarks(95.0);

        Student student = new Student();
        student.setId(1L);

        Subject subject = new Subject();
        subject.setTotalMarks(100);

        Exam exam = new Exam();
        exam.setId(1L);
        exam.setSubject(subject);

        when(studentService.findStudentOrThrow(1L))
                .thenReturn(student);

        when(examService.findExamOrThrow(1L))
                .thenReturn(exam);

        when(resultRepository.existsByStudentIdAndExamId(1L, 1L))
                .thenReturn(true);

        assertThrows(
                BusinessRuleException.class,
                () -> resultService.createResult(request)
        );
    }
    @Test
    void shouldThrowExceptionWhenStudentNotFound() {

        ResultCreateRequest request = new ResultCreateRequest();

        request.setStudentId(999L);
        request.setExamId(1L);
        request.setMarks(95.0);

        when(studentService.findStudentOrThrow(999L))
                .thenThrow(
                        new ResourceNotFoundException(
                                "Student", 999L));

        assertThrows(
                ResourceNotFoundException.class,
                () -> resultService.createResult(request)
        );
    }
    @Test
    void shouldThrowExceptionWhenMarksExceedTotalMarks() {

        ResultCreateRequest request = new ResultCreateRequest();

        request.setStudentId(1L);
        request.setExamId(1L);
        request.setMarks(150.0);

        Student student = new Student();
        student.setId(1L);

        Subject subject = new Subject();
        subject.setTotalMarks(100);

        Exam exam = new Exam();
        exam.setId(1L);
        exam.setSubject(subject);

        when(studentService.findStudentOrThrow(1L))
                .thenReturn(student);

        when(examService.findExamOrThrow(1L))
                .thenReturn(exam);

        when(resultRepository.existsByStudentIdAndExamId(1L, 1L))
                .thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> resultService.createResult(request)
        );
    }
    @Test
    void shouldThrowExceptionWhenExamNotFound() {

        ResultCreateRequest request = new ResultCreateRequest();

        request.setStudentId(1L);
        request.setExamId(999L);
        request.setMarks(95.0);

        Student student = new Student();
        student.setId(1L);

        when(studentService.findStudentOrThrow(1L))
                .thenReturn(student);

        when(examService.findExamOrThrow(999L))
                .thenThrow(
                        new ResourceNotFoundException(
                                "Exam", 999L));

        assertThrows(
                ResourceNotFoundException.class,
                () -> resultService.createResult(request)
        );
    }

    @Test
    void shouldUpdateResultSuccessfully() {
        ResultUpdateRequest request = new ResultUpdateRequest();
        request.setMarks(85.0);

        Student student = new Student();
        student.setId(1L);
        student.setName("Test Student");

        Subject subject = new Subject();
        subject.setTotalMarks(100);

        Exam exam = new Exam();
        exam.setId(1L);
        exam.setSubject(subject);

        Result result = new Result();
        result.setId(1L);
        result.setStudent(student);
        result.setExam(exam);
        result.setMarks(70.0);

        when(resultRepository.findByIdWithDetails(1L))
                .thenReturn(java.util.Optional.of(result));
        when(resultRepository.save(any(Result.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResultResponse response = resultService.updateResult(1L, request);

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertEquals(85.0, response.getMarks());
        org.junit.jupiter.api.Assertions.assertEquals(85.0, response.getPercentage());
    }
}