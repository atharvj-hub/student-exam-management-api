package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.response.ExamResponse;
import com.internship.student_exam_api.dto.response.ResultResponse;
import com.internship.student_exam_api.dto.response.StudentResponse;
import com.internship.student_exam_api.dto.response.SubjectResponse;
import com.internship.student_exam_api.enums.Grade;
import com.internship.student_exam_api.enums.ResultStatus;
import com.internship.student_exam_api.exception.BusinessRuleException;
import com.internship.student_exam_api.security.JwtUtil;
import com.internship.student_exam_api.security.UserDetailsServiceImpl;
import com.internship.student_exam_api.service.ResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResultController.class)
@WithMockUser(roles = "ADMIN")
@org.springframework.context.annotation.Import(com.internship.student_exam_api.security.context.JwtRequestContextFilter.class)
class ResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Mock JwtUtil to satisfy JwtAuthFilter constructor injection in @WebMvcTest slice. */
    @MockBean
    private JwtUtil jwtUtil;

    /** Mock UserDetailsServiceImpl to satisfy JwtAuthFilter constructor injection in @WebMvcTest slice. */
    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private com.internship.student_exam_api.security.context.JwtRequestContext jwtRequestContext;

    @MockBean
    private ResultService resultService;


    @Test
    void createResultReturnsCalculatedResult() throws Exception {
        when(resultService.createResult(any()))
            .thenReturn(resultResponse(1L, new BigDecimal("92.00"), Grade.A_PLUS, ResultStatus.PASS));

        mockMvc.perform(post("/api/results")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"studentId":1,"examId":1,"marks":92}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.percentage").value(92.0))
            .andExpect(jsonPath("$.grade").value("A_PLUS"))
            .andExpect(jsonPath("$.status").value("PASS"));
    }

    @Test
    void duplicateResultReturnsBadRequest() throws Exception {
        when(resultService.createResult(any()))
            .thenThrow(new BusinessRuleException("Result already recorded for student id: 1 and exam id: 1"));

        mockMvc.perform(post("/api/results")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"studentId":1,"examId":1,"marks":92}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("Result already recorded for student id: 1 and exam id: 1"));
    }

    @Test
    void invalidMarksReturnsValidationFailure() throws Exception {
        mockMvc.perform(post("/api/results")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"studentId":1,"examId":1,"marks":-1}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.validationErrors.marks").value("Marks cannot be negative"));
    }

    @Test
    void getByStudentReturnsStudentResults() throws Exception {
        when(resultService.getResultsByStudent(1L))
            .thenReturn(List.of(resultResponse(1L, new BigDecimal("88.00"), Grade.A, ResultStatus.PASS)));

        mockMvc.perform(get("/api/results/student/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].student.id").value(1))
            .andExpect(jsonPath("$[0].grade").value("A"));
    }

    @Test
    void getAllResultsReturnsPaginatedResponse() throws Exception {
        when(resultService.getAllResults(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        resultResponse(1L, new BigDecimal("88.00"), Grade.A, ResultStatus.PASS))));

        mockMvc.perform(get("/api/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].grade").value("A"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void missingStudentIdReturnsValidationFailure() throws Exception {
        mockMvc.perform(post("/api/results")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"examId":1,"marks":70}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.validationErrors.studentId").value("Student ID is required"));
    }

    @Test
    void updateResultReturnsUpdatedResult() throws Exception {
        when(resultService.updateResult(any(), any()))
            .thenReturn(resultResponse(1L, new BigDecimal("92.00"), Grade.A_PLUS, ResultStatus.PASS));

        mockMvc.perform(put("/api/results/1")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"marks":92}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.percentage").value(92.0))
            .andExpect(jsonPath("$.grade").value("A_PLUS"))
            .andExpect(jsonPath("$.status").value("PASS"));
    }

    @Test
    void updateWithNegativeMarksReturnsValidationFailure() throws Exception {
        mockMvc.perform(put("/api/results/1")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"marks":-5}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.validationErrors.marks").value("Marks cannot be negative"));
    }

    @Test
    void updateIgnoresExtraUnknownFields() throws Exception {
        // Sending studentId in the PUT body is a common mistake from API consumers.
        // Jackson ignores unknown fields by default (FAIL_ON_UNKNOWN_PROPERTIES=false),
        // so the operation should succeed as if studentId was not sent.
        when(resultService.updateResult(any(), any()))
            .thenReturn(resultResponse(1L, new BigDecimal("85.00"), Grade.A, ResultStatus.PASS));

        mockMvc.perform(put("/api/results/1")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"marks":85,"studentId":999}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    private ResultResponse resultResponse(Long id, BigDecimal percentage, Grade grade, ResultStatus status) {
        return ResultResponse.builder()
            .id(id)
            .student(StudentResponse.builder()
                .id(1L)
                .name("Test Student")
                .email("test@example.com")
                .rollNumber("ROLL001")
                .build())
            .exam(ExamResponse.builder()
                .id(1L)
                .examName("Final Exam")
                .examDate(LocalDate.of(2026, 6, 3))
                .subject(SubjectResponse.builder()
                    .id(1L)
                    .subjectName("Mathematics")
                    .subjectCode("MATH101")
                    .totalMarks(100)
                    .build())
                .build())
            .marks(percentage)
            .percentage(percentage)
            .grade(grade)
            .status(status)
            .build();
    }
}
