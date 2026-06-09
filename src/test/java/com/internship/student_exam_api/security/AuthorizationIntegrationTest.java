package com.internship.student_exam_api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.student_exam_api.dto.request.StudentCreateRequest;
import com.internship.student_exam_api.entity.AppUser;
import com.internship.student_exam_api.enums.Role;
import com.internship.student_exam_api.security.AppUserDetails;
import com.internship.student_exam_api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.internship.student_exam_api.repository.AppUserRepository appUserRepository;

    private String generateToken(String email, Role role) {
        AppUser appUser = appUserRepository.findByEmail(email).orElseGet(() -> {
            AppUser newUser = new AppUser(email, "password", role);
            return appUserRepository.save(newUser);
        });
        AppUserDetails userDetails = new AppUserDetails(appUser);
        return jwtUtil.generateToken(userDetails);
    }

    @Test
    void whenNoToken_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenStudentRole_thenCanViewStudents() throws Exception {
        String token = generateToken("student@test.com", Role.STUDENT);

        mockMvc.perform(get("/api/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void whenStudentRole_thenCannotCreateStudent() throws Exception {
        String token = generateToken("student@test.com", Role.STUDENT);
        StudentCreateRequest request = new StudentCreateRequest();
        request.setName("Test Student");
        request.setEmail("test@test.com");
        request.setRollNumber("MCA001");

        mockMvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenAdminRole_thenCanCreateStudent() throws Exception {
        String token = generateToken("admin@test.com", Role.ADMIN);
        StudentCreateRequest request = new StudentCreateRequest();
        request.setName("New Student");
        request.setEmail("new@test.com");
        request.setRollNumber("MCA999");

        mockMvc.perform(post("/api/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
