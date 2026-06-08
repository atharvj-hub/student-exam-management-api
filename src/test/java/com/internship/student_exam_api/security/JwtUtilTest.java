package com.internship.student_exam_api.security;

import com.internship.student_exam_api.entity.AppUser;
import com.internship.student_exam_api.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JwtUtil helper class.
 * Ensures signature validation, claims extraction, and expiration behaviors work.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secretKey",
            "dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5TXVzdEJlMzJCeXRlc0xvbmc=");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    private AppUserDetails makeUserDetails(String email, Role role) {
        AppUser user = new AppUser(email, "irrelevant_hash", role);
        return new AppUserDetails(user);
    }

    @Test
    void generatedTokenShouldBeValidAndContainCorrectEmail() {
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);

        assertTrue(jwtUtil.validateToken(token));
        assertEquals("admin@school.com", jwtUtil.extractEmail(token));
    }

    @Test
    void generatedTokenShouldContainCorrectRole() {
        AppUserDetails student = makeUserDetails("student@school.com", Role.STUDENT);
        String token = jwtUtil.generateToken(student);

        assertEquals("STUDENT", jwtUtil.extractRole(token));
    }

    @Test
    void adminTokenShouldContainAdminRole() {
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);

        assertEquals("ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    void tamperedTokenShouldBeInvalid() {
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    void garbageStringShouldBeInvalid() {
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
    }

    @Test
    void expiredTokenShouldBeInvalid() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 1L);
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);

        try { 
            Thread.sleep(10); 
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        assertFalse(jwtUtil.validateToken(token));
    }
}
