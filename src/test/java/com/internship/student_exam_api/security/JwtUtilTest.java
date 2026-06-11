package com.internship.student_exam_api.security;

import com.internship.student_exam_api.entity.AppUser;
import com.internship.student_exam_api.enums.Role;
import com.internship.student_exam_api.security.permission.Permission;
import com.internship.student_exam_api.security.permission.RolePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JwtUtil helper class.
 * Covers token generation, validation, permission serialization/deserialization,
 * and backward compatibility of the deprecated extractRole method.
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

    // ── Token generation & validation ───────────────────────────────────────

    @Test
    @DisplayName("Generated token should be valid and contain correct email")
    void generatedTokenShouldBeValidAndContainCorrectEmail() {
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);

        assertTrue(jwtUtil.validateToken(token));
        assertEquals("admin@school.com", jwtUtil.extractEmail(token));
    }

    @Test
    @DisplayName("Tampered token should be invalid")
    void tamperedTokenShouldBeInvalid() {
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    @DisplayName("Garbage string should be invalid")
    void garbageStringShouldBeInvalid() {
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
    }

    @Test
    @DisplayName("Expired token should be invalid")
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

    // ── Permission extraction ───────────────────────────────────────────────

    @Nested
    @DisplayName("ADMIN permission tests")
    class AdminPermissionTests {

        @Test
        @DisplayName("ADMIN token should contain exactly all 16 permissions")
        void adminTokenShouldContainExactlyAllPermissions() {
            AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
            String token = jwtUtil.generateToken(admin);

            Set<Permission> perms = jwtUtil.extractPermissions(token);

            assertEquals(16, perms.size());
            assertEquals(EnumSet.allOf(Permission.class), perms);
        }

        @Test
        @DisplayName("ADMIN token round-trip: generate → extract → verify matches RolePermissions")
        void adminRoundTripShouldMatchRolePermissions() {
            AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
            String token = jwtUtil.generateToken(admin);

            Set<Permission> fromToken = jwtUtil.extractPermissions(token);
            Set<Permission> fromMapping = RolePermissions.forRole(Role.ADMIN);

            assertEquals(fromMapping, fromToken);
        }
    }

    @Nested
    @DisplayName("STUDENT permission tests")
    class StudentPermissionTests {

        @Test
        @DisplayName("STUDENT token should contain exactly 4 view permissions")
        void studentTokenShouldContainExactly4Permissions() {
            AppUserDetails student = makeUserDetails("student@school.com", Role.STUDENT);
            String token = jwtUtil.generateToken(student);

            Set<Permission> perms = jwtUtil.extractPermissions(token);

            assertEquals(4, perms.size());
            assertTrue(perms.containsAll(Set.of(
                Permission.USER_VIEW, Permission.SUBJECT_VIEW,
                Permission.EXAM_VIEW, Permission.RESULT_VIEW)));
        }

        @Test
        @DisplayName("STUDENT token should NOT contain write permissions")
        void studentTokenShouldNotContainWritePermissions() {
            AppUserDetails student = makeUserDetails("student@school.com", Role.STUDENT);
            String token = jwtUtil.generateToken(student);

            Set<Permission> perms = jwtUtil.extractPermissions(token);

            assertFalse(perms.contains(Permission.USER_DELETE));
            assertFalse(perms.contains(Permission.SUBJECT_CREATE));
            assertFalse(perms.contains(Permission.EXAM_UPDATE));
            assertFalse(perms.contains(Permission.RESULT_CREATE));
            assertFalse(perms.contains(Permission.AI_INSIGHTS_VIEW));
        }

        @Test
        @DisplayName("STUDENT token round-trip should match RolePermissions")
        void studentRoundTripShouldMatchRolePermissions() {
            AppUserDetails student = makeUserDetails("student@school.com", Role.STUDENT);
            String token = jwtUtil.generateToken(student);

            Set<Permission> fromToken = jwtUtil.extractPermissions(token);
            Set<Permission> fromMapping = RolePermissions.forRole(Role.STUDENT);

            assertEquals(fromMapping, fromToken);
        }
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractPermissions edge cases")
    class ExtractPermissionsEdgeCases {

        @Test
        @DisplayName("extractPermissions on token without permissions claim returns empty set")
        void missingPermissionsClaimReturnsEmptySet() {
            // Build a legacy-style token manually (no permissions claim)
            io.jsonwebtoken.Jwts.builder()
                .subject("legacy@school.com")
                .claim("role", "ADMIN")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3600000L))
                .signWith(getTestSigningKey())
                .compact();

            String legacyToken = io.jsonwebtoken.Jwts.builder()
                .subject("legacy@school.com")
                .claim("role", "ADMIN")
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3600000L))
                .signWith(getTestSigningKey())
                .compact();

            Set<Permission> perms = jwtUtil.extractPermissions(legacyToken);

            assertNotNull(perms);
            assertTrue(perms.isEmpty());
        }
    }

    // ── Deprecated extractRole behavior ─────────────────────────────────────

    @Nested
    @DisplayName("Deprecated extractRole tests")
    class ExtractRoleTests {

        @Test
        @DisplayName("extractRole returns null for tokens generated after Phase 1 migration")
        @SuppressWarnings("deprecation")
        void extractRoleReturnsNullForNewTokens() {
            AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
            String token = jwtUtil.generateToken(admin);

            assertNull(jwtUtil.extractRole(token),
                "New tokens no longer contain a 'role' claim");
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private javax.crypto.SecretKey getTestSigningKey() {
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(
            "dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5TXVzdEJlMzJCeXRlc0xvbmc=");
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(keyBytes);
    }
}
