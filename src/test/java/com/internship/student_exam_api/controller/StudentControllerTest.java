package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.response.StudentResponse;
import com.internship.student_exam_api.exception.ResourceNotFoundException;
import com.internship.student_exam_api.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentController.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;

    @Test
    void createStudentReturnsCreatedStudent() throws Exception {
        when(studentService.createStudent(any())).thenReturn(studentResponse(1L));

        mockMvc.perform(post("/api/students")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Test Student","email":"test@example.com","rollNumber":"ROLL001"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void getStudentReturnsStudent() throws Exception {
        when(studentService.getStudentById(1L)).thenReturn(studentResponse(1L));

        mockMvc.perform(get("/api/students/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.rollNumber").value("ROLL001"));
    }

    @Test
    void getAllStudentsReturnsList() throws Exception {
        when(studentService.getAllStudents()).thenReturn(List.of(studentResponse(1L), studentResponse(2L)));

        mockMvc.perform(get("/api/students"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void updateStudentReturnsUpdatedStudent() throws Exception {
        when(studentService.updateStudent(any(), any())).thenReturn(studentResponse(1L));

        mockMvc.perform(put("/api/students/1")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Test Student","email":"test@example.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteStudentReturnsNoContent() throws Exception {
        doNothing().when(studentService).deleteStudent(1L);

        mockMvc.perform(delete("/api/students/1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void validationFailureReturnsUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/api/students")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"email":"not-an-email","rollNumber":"ROLL001"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.validationErrors.name").value("Name is required"))
            .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    void missingStudentReturnsNotFound() throws Exception {
        when(studentService.getStudentById(99L)).thenThrow(new ResourceNotFoundException("Student", 99L));

        mockMvc.perform(get("/api/students/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private StudentResponse studentResponse(Long id) {
        return StudentResponse.builder()
            .id(id)
            .name("Test Student")
            .email("test@example.com")
            .rollNumber("ROLL001")
            .build();
    }
}
