# Security Implementation Plan
## Student Exam Result API — Spring Security + JWT + BCrypt + RBAC + AOP

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [New Dependencies](#new-dependencies)
3. [New File Map](#new-file-map)
4. [Phase 1 — Database Foundation](#phase-1--database-foundation)
5. [Phase 2 — Domain Layer](#phase-2--domain-layer)
6. [Phase 3 — Repository & Spring Security User Model](#phase-3--repository--spring-security-user-model)
7. [Phase 4 — JWT Layer](#phase-4--jwt-layer)
8. [Phase 5 — Authentication Endpoint](#phase-5--authentication-endpoint)
9. [Phase 6 — Security Filter & Configuration](#phase-6--security-filter--configuration)
10. [Phase 7 — AOP Audit Logging](#phase-7--aop-audit-logging)
11. [Phase 8 — Properties & Profiles](#phase-8--properties--profiles)
12. [Phase 9 — Testing](#phase-9--testing)
13. [Migrating Existing Tests](#migrating-existing-tests)
14. [Implementation Order & Timeline](#implementation-order--timeline)
15. [Verification Checklist](#verification-checklist)

---

## Architecture Overview

```
                        ┌─────────────────────────────────────┐
                        │         HTTP REQUEST                 │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────▼──────────────────────┐
                        │        JwtAuthFilter                 │
                        │  (OncePerRequestFilter)              │
                        │                                      │
                        │  1. Extract "Authorization: Bearer"  │
                        │  2. JwtUtil.validateToken()          │
                        │  3. Load AppUserDetails              │
                        │  4. Set SecurityContext              │
                        └──────────────┬──────────────────────┘
                                       │
                        ┌──────────────▼──────────────────────┐
                        │         SecurityConfig               │
                        │  (SecurityFilterChain)               │
                        │                                      │
                        │  /api/auth/**      → PUBLIC          │
                        │  GET /api/**       → ADMIN, STAFF    │
                        │  POST/PUT/DELETE   → ADMIN only      │
                        └──────────────┬──────────────────────┘
                                       │
                 ┌─────────────────────┼───────────────────────┐
                 │                     │                        │
    ┌────────────▼────────┐ ┌──────────▼──────────┐ ┌─────────▼──────────┐
    │   StudentController  │ │  ResultController   │ │   AuthController   │
    │   SubjectController  │ │  ExamController     │ │  POST /api/auth/   │
    └────────────┬─────────┘ └──────────┬──────────┘ └─────────┬──────────┘
                 │                      │                        │
                 └──────────────────────┴────────────────────────┘
                                        │
                        ┌───────────────▼─────────────────────┐
                        │       AuditLoggingAspect             │
                        │  @Around all controller methods      │
                        │  Logs: user, role, method, duration  │
                        └─────────────────────────────────────┘
```

### Access Matrix

| Endpoint Pattern       | HTTP Method         | ADMIN | STAFF | Anonymous |
|------------------------|---------------------|-------|-------|-----------|
| `/api/auth/login`      | POST                | ✅    | ✅    | ✅        |
| `/api/students`        | GET                 | ✅    | ✅    | ❌ 401    |
| `/api/subjects`        | GET                 | ✅    | ✅    | ❌ 401    |
| `/api/exams`           | GET                 | ✅    | ✅    | ❌ 401    |
| `/api/results`         | GET                 | ✅    | ✅    | ❌ 401    |
| `/api/students`        | POST / PUT / DELETE | ✅    | ❌ 403 | ❌ 401   |
| `/api/subjects`        | POST / PUT / DELETE | ✅    | ❌ 403 | ❌ 401   |
| `/api/exams`           | POST / PUT / DELETE | ✅    | ❌ 403 | ❌ 401   |
| `/api/results`         | POST / PUT          | ✅    | ❌ 403 | ❌ 401   |

---

## New Dependencies

Add to `pom.xml` inside `<dependencies>`:

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT — API -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>

<!-- JWT — Implementation (runtime only) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- JWT — Jackson serialization (runtime only) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- AOP — for AuditLoggingAspect -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Spring Security Test — for @WithMockUser in controller tests -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## New File Map

```
src/main/java/com/internship/student_exam_api/
│
├── enums/
│   ├── Grade.java              (existing)
│   ├── ResultStatus.java       (existing)
│   └── Role.java               ← NEW
│
├── entity/
│   ├── Student.java            (existing)
│   ├── Subject.java            (existing)
│   ├── Exam.java               (existing)
│   ├── Result.java             (existing)
│   └── AppUser.java            ← NEW
│
├── repository/
│   ├── StudentRepository.java  (existing)
│   ├── SubjectRepository.java  (existing)
│   ├── ExamRepository.java     (existing)
│   ├── ResultRepository.java   (existing)
│   └── AppUserRepository.java  ← NEW
│
├── security/
│   ├── AppUserDetails.java     ← NEW
│   ├── UserDetailsServiceImpl.java  ← NEW
│   ├── JwtUtil.java            ← NEW
│   └── JwtAuthFilter.java      ← NEW
│
├── dto/
│   ├── request/
│   │   ├── StudentCreateRequest.java  (existing)
│   │   ├── StudentUpdateRequest.java  (existing)
│   │   ├── SubjectCreateRequest.java  (existing)
│   │   ├── SubjectUpdateRequest.java  (existing)
│   │   ├── ExamRequest.java           (existing)
│   │   ├── ResultCreateRequest.java   (existing)
│   │   ├── ResultUpdateRequest.java   (existing)
│   │   └── LoginRequest.java          ← NEW
│   └── response/
│       ├── StudentResponse.java       (existing)
│       ├── SubjectResponse.java       (existing)
│       ├── ExamResponse.java          (existing)
│       ├── ResultResponse.java        (existing)
│       ├── ApiErrorResponse.java      (existing)
│       └── LoginResponse.java         ← NEW
│
├── controller/
│   ├── StudentController.java  (existing)
│   ├── SubjectController.java  (existing)
│   ├── ExamController.java     (existing)
│   ├── ResultController.java   (existing)
│   └── AuthController.java     ← NEW
│
├── config/
│   ├── OpenApiConfig.java      (existing)
│   └── SecurityConfig.java     ← NEW
│
└── aspect/
    └── AuditLoggingAspect.java ← NEW

src/main/resources/db/migration/
├── V1__initial_schema.sql      (existing)
└── V2__create_app_users.sql    ← NEW

src/test/java/com/internship/student_exam_api/
├── security/
│   ├── JwtUtilTest.java        ← NEW
│   └── SecurityIntegrationTest.java  ← NEW
├── controller/
│   ├── StudentControllerTest.java  (existing — needs @WithMockUser)
│   └── ResultControllerTest.java   (existing — needs @WithMockUser)
└── ...
```

---

## Phase 1 — Database Foundation

### `V2__create_app_users.sql`

**File:** `src/main/resources/db/migration/V2__create_app_users.sql`

```sql
-- ═══════════════════════════════════════════════════════════════════════════
-- V2__create_app_users.sql — Flyway Migration
-- ═══════════════════════════════════════════════════════════════════════════
--
-- WHY a separate app_users table and NOT adding auth to the students table?
--
-- students = academic data (name, email, roll number)
-- app_users = system login credentials (email, bcrypt password, role)
--
-- These are different concerns. A student entity should not know about
-- passwords or roles. An admin user might not be a student at all.
-- Separation keeps the domain clean and security concerns isolated.
--
-- SEEDED USERS:
--   admin@school.com / admin123   → Role: ADMIN
--   staff@school.com / staff123   → Role: STAFF
--
-- HOW TO GENERATE BCRYPT HASHES:
--   Option 1 (Java): new BCryptPasswordEncoder().encode("your_password")
--   Option 2 (Online): https://bcrypt-generator.com (cost factor 10)
--   Option 3 (Spring Shell): use the hash printed by the seed generator below
--
-- IMPORTANT: Replace the placeholder hashes below with real BCrypt hashes
-- generated from your chosen passwords before running this migration.

CREATE TABLE IF NOT EXISTS app_users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(150) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'STAFF')),
    created_at TIMESTAMP    DEFAULT NOW()
);

-- Seed admin user
-- Password: admin123  →  replace with actual BCrypt hash
INSERT INTO app_users (email, password, role) VALUES (
    'admin@school.com',
    '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH_FOR_admin123',
    'ADMIN'
);

-- Seed staff user
-- Password: staff123  →  replace with actual BCrypt hash
INSERT INTO app_users (email, password, role) VALUES (
    'staff@school.com',
    '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH_FOR_staff123',
    'STAFF'
);
```

> **Generating real hashes:** Add a temporary `main()` method or unit test that prints
> `new BCryptPasswordEncoder().encode("admin123")` and copy the output into the SQL.
> Each run produces a different hash — BCrypt is salted by design.

---

## Phase 2 — Domain Layer

### `Role.java`

**File:** `src/main/java/com/internship/student_exam_api/enums/Role.java`

```java
package com.internship.student_exam_api.enums;

/**
 * Application roles for RBAC.
 *
 * ADMIN → full read + write access to all endpoints
 * STAFF → read-only access to all endpoints
 *
 * Spring Security stores these as GrantedAuthority with "ROLE_" prefix:
 *   Role.ADMIN → GrantedAuthority("ROLE_ADMIN")
 * This prefix is required for hasRole("ADMIN") to work in SecurityConfig.
 * hasRole("ADMIN") internally checks for "ROLE_ADMIN".
 */
public enum Role {
    ADMIN,
    STAFF
}
```

---

### `AppUser.java`

**File:** `src/main/java/com/internship/student_exam_api/entity/AppUser.java`

```java
package com.internship.student_exam_api.entity;

import com.internship.student_exam_api.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * AppUser entity — maps to the "app_users" table.
 *
 * This is a SYSTEM USER, not a student.
 * Represents someone who can log into this API:
 *   ADMIN (teacher, registrar) → full access
 *   STAFF (department head)    → read-only
 *
 * password is stored as a BCrypt hash — NEVER plain text.
 *   BCrypt hash example: $2a$10$... (60 chars always)
 *   VARCHAR(255) safely holds any BCrypt hash.
 *
 * @Enumerated(STRING) → stores "ADMIN" or "STAFF", not 0 or 1.
 *   Adding new roles is safe — existing rows are unaffected.
 */
@Entity
@Table(
    name = "app_users",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "email", name = "uk_app_user_email")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /**
     * BCrypt hash — never the raw password.
     * LENGTH: BCrypt always produces exactly 60 characters.
     *   VARCHAR(255) gives plenty of room for future algorithm changes.
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public AppUser(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }
}
```

---

## Phase 3 — Repository & Spring Security User Model

### `AppUserRepository.java`

**File:** `src/main/java/com/internship/student_exam_api/repository/AppUserRepository.java`

```java
package com.internship.student_exam_api.repository;

import com.internship.student_exam_api.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for AppUser — authentication data access.
 *
 * findByEmail is the critical method:
 *   Spring Security calls UserDetailsService.loadUserByUsername(email)
 *   which internally calls this method.
 *
 *   Generated SQL: SELECT * FROM app_users WHERE email = ?
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);
}
```

---

### `AppUserDetails.java`

**File:** `src/main/java/com/internship/student_exam_api/security/AppUserDetails.java`

```java
package com.internship.student_exam_api.security;

import com.internship.student_exam_api.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Bridge between our AppUser entity and Spring Security's UserDetails contract.
 *
 * Spring Security knows nothing about our AppUser class.
 * It operates on UserDetails objects.
 * This adapter class wraps AppUser and exposes what Spring Security needs:
 *   - username (we use email)
 *   - password (BCrypt hash)
 *   - authorities (roles with ROLE_ prefix)
 *
 * WHY NOT implement UserDetails directly on AppUser?
 *   It would couple the JPA entity to Spring Security.
 *   Swapping auth frameworks would require changing the entity.
 *   Adapter pattern keeps them independent.
 *
 * getAuthorities():
 *   Spring Security requires role strings prefixed with "ROLE_".
 *   Role.ADMIN → "ROLE_ADMIN"
 *   hasRole("ADMIN") in SecurityConfig checks for "ROLE_ADMIN".
 */
public class AppUserDetails implements UserDetails {

    private final AppUser appUser;

    public AppUserDetails(AppUser appUser) {
        this.appUser = appUser;
    }

    /**
     * Returns the role as a GrantedAuthority with ROLE_ prefix.
     * Spring Security's hasRole("ADMIN") checks for "ROLE_ADMIN".
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
            new SimpleGrantedAuthority("ROLE_" + appUser.getRole().name())
        );
    }

    @Override
    public String getPassword() {
        return appUser.getPassword(); // BCrypt hash
    }

    /**
     * We use email as the username identifier.
     * Spring Security's SecurityContext stores this as Principal.getName().
     */
    @Override
    public String getUsername() {
        return appUser.getEmail();
    }

    @Override
    public boolean isAccountNonExpired()  { return true; }

    @Override
    public boolean isAccountNonLocked()   { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }

    /** Expose role for JWT claim building and response DTOs. */
    public String getRoleName() {
        return appUser.getRole().name();
    }
}
```

---

### `UserDetailsServiceImpl.java`

**File:** `src/main/java/com/internship/student_exam_api/security/UserDetailsServiceImpl.java`

```java
package com.internship.student_exam_api.security;

import com.internship.student_exam_api.repository.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of Spring Security's UserDetailsService.
 *
 * Spring Security calls loadUserByUsername() at two points:
 *   1. Login: AuthenticationManager needs to verify credentials.
 *   2. Every request: JwtAuthFilter reconstructs SecurityContext from JWT.
 *
 * Both cases pass the email (our "username") to this method.
 * We load the AppUser from DB and wrap it in AppUserDetails.
 *
 * @Transactional(readOnly = true): this is a DB read — mark it as such
 * for Hibernate session optimization.
 */
@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public UserDetailsServiceImpl(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);
        return appUserRepository.findByEmail(email)
            .map(AppUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException(
                "No user found with email: " + email
            ));
    }
}
```

---

## Phase 4 — JWT Layer

### `JwtUtil.java`

**File:** `src/main/java/com/internship/student_exam_api/security/JwtUtil.java`

```java
package com.internship.student_exam_api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT utility: generate, validate, and extract claims from tokens.
 *
 * ALGORITHM: HS256 (HMAC-SHA-256)
 *   Symmetric — same key signs and verifies.
 *   Simple and sufficient for a single-server API.
 *   For multi-service or third-party verification, consider RS256 (asymmetric).
 *
 * TOKEN STRUCTURE:
 *   Header:  { "alg": "HS256" }
 *   Payload: { "sub": "admin@school.com", "role": "ADMIN",
 *              "iat": <issued at>, "exp": <expiry> }
 *   Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
 *
 * SECRET KEY REQUIREMENTS:
 *   HS256 requires at least 256-bit (32-byte) key.
 *   Store as Base64-encoded string in application properties.
 *   NEVER commit the production secret to version control.
 *
 * GENERATE A SECURE KEY (run once):
 *   import io.jsonwebtoken.security.Keys;
 *   import io.jsonwebtoken.io.Encoders;
 *   String key = Encoders.BASE64.encode(Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded());
 *   System.out.println(key);  // → paste into application-prod.properties
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Generate a signed JWT for the authenticated user.
     *
     * Claims included:
     *   sub   → email (Spring Security's "username")
     *   role  → "ADMIN" or "STAFF"
     *   iat   → issued-at timestamp
     *   exp   → expiry timestamp
     */
    public String generateToken(AppUserDetails userDetails) {
        return Jwts.builder()
            .subject(userDetails.getUsername())
            .claim("role", userDetails.getRoleName())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Validate token: signature must match, token must not be expired.
     * Returns true only if both checks pass.
     *
     * JwtException covers: MalformedJwtException, ExpiredJwtException,
     *   SignatureException, UnsupportedJwtException.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /** Extract email (stored as "sub" claim) from token. */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /** Extract role string ("ADMIN" or "STAFF") from token. */
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Decode the Base64-encoded secret and build an HMAC key.
     * Called on every token operation — cheap since it's just bytes.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

---

## Phase 5 — Authentication Endpoint

### `LoginRequest.java`

**File:** `src/main/java/com/internship/student_exam_api/dto/request/LoginRequest.java`

```java
package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
```

---

### `LoginResponse.java`

**File:** `src/main/java/com/internship/student_exam_api/dto/response/LoginResponse.java`

```java
package com.internship.student_exam_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Returned on successful login.
 *
 * The client (React frontend, Postman, Python script) must:
 *   1. Store the token (localStorage, memory, or httpOnly cookie)
 *   2. Include it on every subsequent request:
 *      Authorization: Bearer <token>
 *
 * tokenType is always "Bearer" — standard JWT auth convention.
 */
@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;  // always "Bearer"
    private String role;       // "ADMIN" or "STAFF"
    private String email;
    private long expiresIn;    // milliseconds until expiry (for client-side timer)
}
```

---

### `AuthController.java`

**File:** `src/main/java/com/internship/student_exam_api/controller/AuthController.java`

```java
package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.LoginRequest;
import com.internship.student_exam_api.dto.response.LoginResponse;
import com.internship.student_exam_api.security.AppUserDetails;
import com.internship.student_exam_api.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoint — the only public API path.
 *
 * HOW LOGIN WORKS:
 *
 *   POST /api/auth/login  { "email": "admin@school.com", "password": "admin123" }
 *          │
 *          ▼
 *   AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)
 *          │
 *          ▼ Spring Security internally calls:
 *   UserDetailsServiceImpl.loadUserByUsername(email)  → loads AppUser from DB
 *          │
 *          ▼
 *   BCryptPasswordEncoder.matches(rawPassword, storedHash)
 *          │
 *          ├── FAIL → throws BadCredentialsException → GlobalExceptionHandler → 401
 *          │
 *          └── PASS → Authentication object returned
 *                  │
 *                  ▼
 *             JwtUtil.generateToken(userDetails) → JWT string
 *                  │
 *                  ▼
 *             LoginResponse { token, role, email, expiresIn }
 *                  │
 *                  ▼
 *             HTTP 200 OK
 *
 * CONTROLLER STAYS THIN:
 *   No password checking here. AuthenticationManager handles that.
 *   No DB access here. UserDetailsServiceImpl handles that.
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // authenticate() throws BadCredentialsException on wrong password
        // GlobalExceptionHandler maps that to HTTP 401
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        log.info("Login successful for email: {}, role: {}", userDetails.getUsername(), userDetails.getRoleName());

        return ResponseEntity.ok(
            LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role(userDetails.getRoleName())
                .email(userDetails.getUsername())
                .expiresIn(expirationMs)
                .build()
        );
    }
}
```

---

## Phase 6 — Security Filter & Configuration

### `JwtAuthFilter.java`

**File:** `src/main/java/com/internship/student_exam_api/security/JwtAuthFilter.java`

```java
package com.internship.student_exam_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs on EVERY incoming HTTP request, exactly once.
 *
 * OncePerRequestFilter guarantees single execution per request,
 * even if the filter chain invokes the filter multiple times
 * (can happen with forward dispatchers).
 *
 * FILTER FLOW:
 *
 *   Request arrives
 *       │
 *       ▼
 *   Extract "Authorization: Bearer <token>" header
 *       │
 *       ├── Header absent or doesn't start with "Bearer " → skip, continue chain
 *       │   (Spring Security will reject the request as 401 if endpoint requires auth)
 *       │
 *       ▼
 *   JwtUtil.validateToken(token)
 *       │
 *       ├── Invalid/expired → log warning, skip (SecurityContext stays empty → 401)
 *       │
 *       ▼
 *   Extract email from token
 *       │
 *       ▼
 *   Check SecurityContext isn't already populated (prevents double-auth)
 *       │
 *       ▼
 *   UserDetailsServiceImpl.loadUserByUsername(email)
 *       │
 *       ▼
 *   Build UsernamePasswordAuthenticationToken with authorities
 *       │
 *       ▼
 *   SecurityContextHolder.getContext().setAuthentication(auth)
 *       │
 *       ▼
 *   Continue filter chain → Controller receives authenticated request
 *
 * WHY NOT authenticate in a Spring Security AuthenticationProvider?
 *   JWT is stateless — there's no AuthenticationManager involved here.
 *   We already validated the signature. We just need to SET the authentication.
 *   An AuthenticationProvider is for the LOGIN step (AuthController), not every request.
 */
@Component
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String email = jwtUtil.extractEmail(token);

            // Only set auth if not already authenticated
            // (avoids redundant DB lookups on internally forwarded requests)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,               // credentials null — token is the credential
                        userDetails.getAuthorities()
                    );

                // Attach request metadata (IP, session ID) to the auth object
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Set authentication for user: {} on path: {}",
                    email, request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT from Authorization header.
     * Expected format: "Authorization: Bearer eyJhbGciOiJ..."
     *
     * Returns null if header is absent or malformed.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // remove "Bearer " prefix (7 chars)
        }
        return null;
    }
}
```

---

### `SecurityConfig.java`

**File:** `src/main/java/com/internship/student_exam_api/config/SecurityConfig.java`

```java
package com.internship.student_exam_api.config;

import com.internship.student_exam_api.security.JwtAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration — SecurityFilterChain approach.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. STATELESS session:
 *    Spring Security won't create HttpSessions.
 *    Each request is authenticated independently via JWT.
 *    No JSESSIONID cookies. No server-side session state.
 *
 * 2. CSRF disabled:
 *    CSRF attacks exploit stateful sessions with cookies.
 *    Since we're stateless + JWT (no cookies), CSRF doesn't apply.
 *    Disabling it removes the CSRF token requirement from non-GET requests.
 *
 * 3. JwtAuthFilter BEFORE UsernamePasswordAuthenticationFilter:
 *    Spring's default filter processes form logins.
 *    Our filter runs first to handle JWT auth from Authorization header.
 *    addFilterBefore() inserts our filter in the correct position.
 *
 * 4. DaoAuthenticationProvider:
 *    Wires UserDetailsService + PasswordEncoder together.
 *    Spring's AuthenticationManager delegates to this provider during login.
 *    Without this explicit bean, Spring may not pick up BCrypt automatically.
 *
 * 5. @EnableMethodSecurity:
 *    Enables @PreAuthorize, @PostAuthorize, @Secured on individual methods.
 *    Currently unused — added for future fine-grained access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // Disable CSRF — stateless JWT API, no cookies
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless sessions — no HttpSession, no JSESSIONID
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: login endpoint
                .requestMatchers("/api/auth/**").permitAll()

                // Public: Swagger UI (development convenience — lock down in production)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Read access: both ADMIN and STAFF
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("ADMIN", "STAFF")

                // Write access: ADMIN only
                .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")

                // Anything else requires authentication
                .anyRequest().authenticated()
            )

            // Insert JWT filter before Spring's default login filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    /**
     * BCryptPasswordEncoder — the standard for production password hashing.
     *
     * cost factor = 10 (default):
     *   Each hash takes ~100ms to compute.
     *   Brute force of 1 billion passwords/second → ~317 years per hash.
     *   Increase to 12 for extra security (but 400ms per login — acceptable).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * DaoAuthenticationProvider wires UserDetailsService + PasswordEncoder.
     * Spring's AuthenticationManager uses this provider to verify login credentials.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * AuthenticationManager bean — exposed for AuthController to inject.
     * AuthController calls authenticationManager.authenticate(credentials).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

---

### Update `GlobalExceptionHandler.java`

Add a handler for `BadCredentialsException` (wrong password → 401) and `AccessDeniedException` (wrong role → 403):

```java
// ─── 401 Unauthorized — Bad Credentials ──────────────────────────────────────
// Add this import: org.springframework.security.authentication.BadCredentialsException;
@ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
public ResponseEntity<ApiErrorResponse> handleBadCredentials(
        org.springframework.security.authentication.BadCredentialsException ex) {
    log.warn("Authentication failed: bad credentials");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
        ApiErrorResponse.builder()
            .status(401)
            .error("UNAUTHORIZED")
            .message("Invalid email or password")
            .timestamp(LocalDateTime.now())
            .build()
    );
}

// ─── 403 Forbidden — Insufficient Role ────────────────────────────────────────
// Add this import: org.springframework.security.access.AccessDeniedException;
@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
public ResponseEntity<ApiErrorResponse> handleAccessDenied(
        org.springframework.security.access.AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
        ApiErrorResponse.builder()
            .status(403)
            .error("FORBIDDEN")
            .message("You do not have permission to perform this action")
            .timestamp(LocalDateTime.now())
            .build()
    );
}
```

---

## Phase 7 — AOP Audit Logging

### `AuditLoggingAspect.java`

**File:** `src/main/java/com/internship/student_exam_api/aspect/AuditLoggingAspect.java`

```java
package com.internship.student_exam_api.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AuditLoggingAspect — cross-cutting audit trail for all controller methods.
 *
 * HOW AOP WORKS HERE:
 *
 *   Without AOP: you'd add log.info("[AUDIT] user=...") to every controller method.
 *   That's repetitive (DRY violation) and easy to forget on new methods.
 *
 *   With AOP: one @Around advice intercepts every controller method automatically.
 *   Adding a new controller method instantly gains audit logging for free.
 *
 * @Aspect → Spring: treat this class as an AOP aspect (needs spring-boot-starter-aop)
 * @Component → Register as a Spring Bean
 *
 * @Around vs @Before vs @After:
 *   @Before  → runs before, can't measure duration or see result
 *   @After   → runs after, but doesn't see exceptions
 *   @Around  → full control: wraps the method, measures time, catches exceptions
 *   We use @Around because we need duration AND we want to log failed calls too.
 *
 * POINTCUT EXPRESSION:
 *   "execution(* com.internship.student_exam_api.controller.*.*(..))"
 *   Matches: any return type (*) | in the controller package | any class (.*) |
 *            any method name (.*) | any parameters (..)
 *
 * LOG FORMAT:
 *   [AUDIT] user=admin@school.com role=ROLE_ADMIN
 *           method=StudentController.createStudent(..) duration=45ms status=SUCCESS
 *
 * This log output is ideal for:
 *   - Security audits ("who deleted student #12?")
 *   - Performance monitoring ("which endpoint is slow?")
 *   - Integration with ELK stack or CloudWatch Logs
 */
@Aspect
@Component
@Slf4j
public class AuditLoggingAspect {

    @Around("execution(* com.internship.student_exam_api.controller.*.*(..))")
    public Object auditControllerCall(ProceedingJoinPoint joinPoint) throws Throwable {
        // Extract authenticated user from SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = "anonymous";
        String role  = "NONE";

        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {
            email = authentication.getName();
            role = authentication.getAuthorities().isEmpty()
                ? "NONE"
                : authentication.getAuthorities().iterator().next().getAuthority();
        }

        String method = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed(); // Execute the actual controller method

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AUDIT] user={} role={} method={} duration={}ms status=SUCCESS",
                email, role, method, duration);

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[AUDIT] user={} role={} method={} duration={}ms status=ERROR error={}",
                email, role, method, duration, e.getClass().getSimpleName());

            throw e; // Re-throw — let GlobalExceptionHandler handle it
        }
    }
}
```

---

## Phase 8 — Properties & Profiles

### `application-dev.properties` — add JWT config

```properties
# ============================================================================
# JWT Configuration
# ============================================================================

# Base64-encoded 256-bit secret key for HS256
# GENERATE YOUR OWN: run Keys.secretKeyFor() and encode as Base64
# This default is for local development ONLY — never use in production
jwt.secret=dGhpcyBpcyBhIHNlY3JldCBrZXkgZm9yIGRldmVsb3BtZW50IG9ubHkgMTIzNA==
jwt.expiration-ms=86400000
```

> **Generate a real 256-bit key:**
> ```java
> import io.jsonwebtoken.security.Keys;
> import io.jsonwebtoken.io.Encoders;
> System.out.println(
>     Encoders.BASE64.encode(
>         Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256).getEncoded()
>     )
> );
> ```

### `application-test.properties` — add JWT config

```properties
# JWT config for tests (short expiry)
jwt.secret=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5TXVzdEJlMzJCeXRlc0xvbmc=
jwt.expiration-ms=3600000
```

### `application-prod.properties` — use environment variables

```properties
# In production: inject from AWS Secrets Manager / Kubernetes Secret / CI env vars
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=${JWT_EXPIRATION_MS:86400000}

# NEVER hardcode the production secret in this file
```

---

## Phase 9 — Testing

### `JwtUtilTest.java`

**File:** `src/test/java/com/internship/student_exam_api/security/JwtUtilTest.java`

```java
package com.internship.student_exam_api.security;

import com.internship.student_exam_api.entity.AppUser;
import com.internship.student_exam_api.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Must be Base64-encoded and at least 256 bits (32 bytes)
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
        AppUserDetails staff = makeUserDetails("staff@school.com", Role.STAFF);
        String token = jwtUtil.generateToken(staff);

        assertEquals("STAFF", jwtUtil.extractRole(token));
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
        // Override expiration to 1ms — token expires immediately
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 1L);
        AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
        String token = jwtUtil.generateToken(admin);

        // Wait for expiry
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        assertFalse(jwtUtil.validateToken(token));
    }
}
```

---

### `SecurityIntegrationTest.java`

**File:** `src/test/java/com/internship/student_exam_api/security/SecurityIntegrationTest.java`

```java
package com.internship.student_exam_api.security;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Full-stack security integration tests.
 * Uses H2 in-memory DB (test profile).
 *
 * Tests the complete login → JWT → protected endpoint flow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository appUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void seedUsers() {
        appUserRepository.deleteAll();
        appUserRepository.save(new AppUser(
            "admin@test.com",
            passwordEncoder.encode("admin123"),
            Role.ADMIN
        ));
        appUserRepository.save(new AppUser(
            "staff@test.com",
            passwordEncoder.encode("staff123"),
            Role.STAFF
        ));
    }

    // ── ST01: Successful login returns JWT ───────────────────────────────────
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

    // ── ST02: Wrong password returns 401 ────────────────────────────────────
    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"wrongpassword\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // ── ST03: No token returns 401 ───────────────────────────────────────────
    @Test
    void requestWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isUnauthorized());
    }

    // ── ST04: STAFF can read ─────────────────────────────────────────────────
    @Test
    void staffTokenShouldAllowGetEndpoints() throws Exception {
        String token = loginAndGetToken("staff@test.com", "staff123");

        mockMvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    // ── ST05: STAFF cannot write (POST) → 403 ───────────────────────────────
    @Test
    void staffTokenShouldForbidPostEndpoints() throws Exception {
        String token = loginAndGetToken("staff@test.com", "staff123");

        mockMvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"email\":\"t@t.com\",\"rollNumber\":\"R1\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ── ST06: ADMIN can write (POST) → 201 ──────────────────────────────────
    @Test
    void adminTokenShouldAllowPostEndpoints() throws Exception {
        String token = loginAndGetToken("admin@test.com", "admin123");

        mockMvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Student\",\"email\":\"test@school.com\","
                       + "\"rollNumber\":\"CS001\"}"))
            .andExpect(status().isCreated());
    }

    // ── ST07: Tampered token returns 401 ────────────────────────────────────
    @Test
    void tamperedTokenShouldReturn401() throws Exception {
        String token = loginAndGetToken("admin@test.com", "admin123");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        mockMvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized());
    }

    // ── ST08: ADMIN can delete → 204 ────────────────────────────────────────
    @Test
    void adminTokenShouldAllowDeleteEndpoints() throws Exception {
        String token = loginAndGetToken("admin@test.com", "admin123");

        mockMvc.perform(delete("/api/students/99999")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound()); // 404 means auth passed, resource missing
    }

    // ── Helper: login and extract token ─────────────────────────────────────
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
```

---

## Migrating Existing Tests

When Spring Security is added, `@WebMvcTest` tests will start receiving **401 Unauthorized** on every request. Fix them by adding `@WithMockUser`:

### `StudentControllerTest.java` — changes needed

Add the import and annotate each test method (or the class):

```java
import org.springframework.security.test.context.support.WithMockUser;

// For the whole class (all tests run as ADMIN):
@WebMvcTest(StudentController.class)
@WithMockUser(roles = "ADMIN")      // ← add this
class StudentControllerTest {
    // ... all existing tests work without changes
}
```

Or per-method for more precision:

```java
@Test
@WithMockUser(roles = "ADMIN")     // write operation
void createStudentReturnsCreatedStudent() throws Exception { ... }

@Test
@WithMockUser(roles = "STAFF")     // read operation — staff should also work
void getStudentReturnsStudent() throws Exception { ... }
```

### `ResultControllerTest.java` — same pattern

```java
@WebMvcTest(ResultController.class)
@WithMockUser(roles = "ADMIN")
class ResultControllerTest {
    // ... no other changes required
}
```

### `ResultServiceTest.java` — no changes

Unit tests (pure Java, no Spring context) are unaffected by security.

### `StudentRepositoryTest.java` and `ResultRepositoryTest.java` — no changes

`@DataJpaTest` tests don't load the web layer or security configuration.

---

## Implementation Order & Timeline

### Saturday Morning (Foundation)

```
[ ] 1. Add all new dependencies to pom.xml — verify mvn compile passes
[ ] 2. Create Role.java enum
[ ] 3. Create AppUser.java entity
[ ] 4. Write V2__create_app_users.sql migration
[ ] 5. Generate BCrypt hashes and update the SQL seed values
[ ] 6. Run mvn spring-boot:run — verify Flyway runs V2 cleanly
```

### Saturday Afternoon (Auth Core)

```
[ ] 7.  Create AppUserRepository.java
[ ] 8.  Create AppUserDetails.java
[ ] 9.  Create UserDetailsServiceImpl.java
[ ] 10. Create JwtUtil.java
[ ] 11. Create LoginRequest.java and LoginResponse.java
[ ] 12. Create AuthController.java
[ ] 13. Test login manually: POST /api/auth/login — should get a token
```

### Sunday Morning (Security Filter)

```
[ ] 14. Create JwtAuthFilter.java
[ ] 15. Create SecurityConfig.java
[ ] 16. Add 401/403 handlers to GlobalExceptionHandler.java
[ ] 17. Update application-dev.properties with JWT config
[ ] 18. Update application-test.properties with JWT config
[ ] 19. Fix existing controller tests: add @WithMockUser
[ ] 20. Run mvn test — all tests must pass
```

### Sunday Afternoon (AOP + Verification)

```
[ ] 21. Create AuditLoggingAspect.java
[ ] 22. Verify audit log appears in console on API calls
[ ] 23. Write JwtUtilTest.java unit tests
[ ] 24. Write SecurityIntegrationTest.java
[ ] 25. Run full test suite: mvn test
[ ] 26. Manual Postman/Swagger verification (checklist below)
```

---

## Verification Checklist

Run these in Postman or with `curl` against `http://localhost:8080`:

```
Login
  [ ] POST /api/auth/login { "email": "admin@school.com", "password": "admin123" }
      → 200 OK, response has "token" field

  [ ] POST /api/auth/login { "email": "admin@school.com", "password": "wrongpass" }
      → 401 Unauthorized, error: "UNAUTHORIZED"

  [ ] POST /api/auth/login { "email": "", "password": "" }
      → 422 Unprocessable, validationErrors

No Token
  [ ] GET /api/students (no Authorization header)
      → 401 Unauthorized

  [ ] POST /api/students (no Authorization header)
      → 401 Unauthorized

STAFF Token
  [ ] GET /api/students  (Authorization: Bearer <staff_token>)
      → 200 OK

  [ ] GET /api/results   (Authorization: Bearer <staff_token>)
      → 200 OK

  [ ] POST /api/students (Authorization: Bearer <staff_token>)
      → 403 Forbidden, error: "FORBIDDEN"

  [ ] DELETE /api/students/1 (Authorization: Bearer <staff_token>)
      → 403 Forbidden

ADMIN Token
  [ ] GET /api/students  (Authorization: Bearer <admin_token>)
      → 200 OK

  [ ] POST /api/students (Authorization: Bearer <admin_token>)
      → 201 Created (or 409/422 depending on payload)

  [ ] DELETE /api/students/99999 (Authorization: Bearer <admin_token>)
      → 404 Not Found (401/403 would mean auth failed — 404 means auth passed)

Token Integrity
  [ ] GET /api/students with tampered token (change last 5 chars)
      → 401 Unauthorized

Audit Logging
  [ ] Any authenticated API call → check console for "[AUDIT] user=... role=..."
  [ ] Failed API call (404, 400) → check console for "[AUDIT] ... status=ERROR"

mvn test
  [ ] All ResultServiceTest tests pass
  [ ] All JwtUtilTest tests pass
  [ ] All SecurityIntegrationTest tests pass
  [ ] All StudentControllerTest tests pass (with @WithMockUser)
  [ ] All ResultControllerTest tests pass (with @WithMockUser)
  [ ] All repository tests pass
```

---

## Deliverables Summary

```
JWT Authentication (HS256)      ✅  JwtUtil + JwtAuthFilter
BCrypt Password Hashing         ✅  SecurityConfig + V2 migration
RBAC (Admin / Staff)            ✅  SecurityConfig + AppUser + Role
Login Endpoint                  ✅  AuthController + LoginRequest/Response
Global 401/403 Error Handling   ✅  GlobalExceptionHandler
AOP Audit Logging               ✅  AuditLoggingAspect
Flyway-managed user schema      ✅  V2__create_app_users.sql
Security Test Suite             ✅  JwtUtilTest + SecurityIntegrationTest
Existing tests preserved        ✅  @WithMockUser migration
Stateless session               ✅  STATELESS in SecurityConfig
Swagger still accessible        ✅  /swagger-ui/** permitted
```
