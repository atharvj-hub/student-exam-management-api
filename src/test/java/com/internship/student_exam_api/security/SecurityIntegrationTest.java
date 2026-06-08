package com.internship.student_exam_api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.student_exam_api.entity.AppUser;
import com.internship.student_exam_api.enums.Role;
import com.internship.student_exam_api.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying the security configurations, filter checks, JWT validations,
 * and Role-Based Access Control logic across multiple endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedUsers() {
        appUserRepository.deleteAll();
        appUserRepository.save(new AppUser(
            "admin@test.com",
            passwordEncoder.encode("admin123"),
            Role.ADMIN
        ));
        appUserRepository.save(new AppUser(
            "student@test.com",
            passwordEncoder.encode("student123"),
            Role.STUDENT
        ));
    }

    @Test
    void loginWithValidCredentialsShouldReturnToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"wrongpassword\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void requestWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentTokenShouldAllowGetEndpoints() throws Exception {
        String token = loginAndGetToken("student@test.com", "student123");

        mockMvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void studentTokenShouldForbidPostEndpoints() throws Exception {
        String token = loginAndGetToken("student@test.com", "student123");

        mockMvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"email\":\"t@t.com\",\"rollNumber\":\"R1\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void adminTokenShouldAllowPostEndpoints() throws Exception {
        String token = loginAndGetToken("admin@test.com", "admin123");

        mockMvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Student\",\"email\":\"test@school.com\",\"rollNumber\":\"CS001\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void tamperedTokenShouldReturn401() throws Exception {
        String token = loginAndGetToken("admin@test.com", "admin123");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        mockMvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenShouldAllowDeleteEndpoints() throws Exception {
        String token = loginAndGetToken("admin@test.com", "admin123");

        mockMvc.perform(delete("/api/students/99999")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

        Map<?, ?> response = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("token");
    }
}
