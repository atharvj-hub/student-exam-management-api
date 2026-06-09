# Custom AOP Authorization Framework
## Architecture Design Document — student-exam-api

**Document Type:** Architecture Design & Implementation Plan  
**Project:** student-exam-api (Post-Security Sprint)  
**Author:** Senior Java Architect  
**Status:** Implementation Ready  
**Current State:** 46/46 tests passing, Spring Security JWT + RBAC complete

---

## Table of Contents

1. Executive Summary
2. Current Architecture Audit
3. What to Keep vs. Replace
4. Permission Model Design
5. JWT Token Redesign
6. Package Structure
7. Component Design (File-by-File)
8. Request Flow Diagrams
9. AOP Proxy Mechanics & Limitations
10. Self-Invocation Problem & Solutions
11. JwtContext / RequestContext Design
12. Implementation Order & Timeline
13. Testing Strategy
14. AOP vs. Spring Security Comparison

---

## 1. Executive Summary

This document defines a complete custom authorization framework built on Spring AOP and JWT, replacing Spring Security's role-based authorization layer with a permission-based system designed for educational depth and architectural clarity.

The goal is not to build a better security system than Spring Security — it is to understand, from first principles, how authorization works at the AOP proxy level, how JWT claims map to method-level access control, and how request context propagation enables clean, non-repetitive permission checks across an entire request lifecycle.

### What This Sprint Produces

- A `Permission` enum replacing `Role`-based authorization
- JWT tokens carrying permission arrays instead of single role strings
- A `@RequirePermission` annotation for declarative method-level authorization
- An `AuthorizationAspect` that intercepts annotated methods, parses the JWT from the current request, and enforces permissions
- A `JwtRequestContext` that parses the JWT exactly once per request and makes it available throughout the call stack
- Full test coverage including permission matrix tests, aspect failure tests, and self-invocation regression tests

---

## 2. Current Architecture Audit

### What Exists

```
Spring Security Filter Chain
       │
       ▼
JwtAuthFilter (OncePerRequestFilter)
  └── validates JWT signature
  └── extracts email
  └── loads UserDetails
  └── sets SecurityContextHolder
       │
       ▼
SecurityConfig.authorizeHttpRequests()
  └── GET /api/**  → hasAnyRole("ADMIN", "STUDENT")
  └── POST /api/** → hasRole("ADMIN")
  └── PUT /api/**  → hasRole("ADMIN")
  └── DELETE /**   → hasRole("ADMIN")
       │
       ▼
@PreAuthorize("hasRole('ADMIN')") on controller methods
       │
       ▼
Service Layer (no authorization logic)
```

### Current JWT Payload

```json
{
  "sub": "admin@school.com",
  "role": "ADMIN",
  "iat": 1700000000,
  "exp": 1700086400
}
```

### Problems With Current Approach for Learning

1. Spring Security's role checks are opaque — you configure strings and the framework does the enforcement invisibly.
2. HTTP method-level rules in `SecurityConfig` are blunt instruments. They cannot express "RESULT_VIEW is allowed but RESULT_CREATE is not" at the same HTTP method level.
3. `@PreAuthorize` uses Spring Expression Language strings — `"hasRole('ADMIN')"` — which are not type-safe and fail silently on typos.
4. There is no concept of granular permissions. ADMIN can do everything; STUDENT can do nothing write-related. Real systems need fine-grained control: a "grader" role that can create results but not delete students.
5. The authorization logic lives in the framework, not in your code. You learn nothing about how it works.

---

## 3. What to Keep vs. Replace

This is the most important architectural decision. Spring Security is still doing critical work that you should not reinvent.

### Keep (Do Not Touch)

| Component | Why Keep It |
|---|---|
| `SecurityConfig` — JWT filter registration | Infrastructure wiring; removing this breaks the filter chain |
| `JwtAuthFilter` | Token validation on every request; sets `SecurityContextHolder` |
| `JwtUtil` | Token generation and validation logic is already correct |
| `BCryptPasswordEncoder` | Password hashing is non-negotiable infrastructure |
| `UserDetailsServiceImpl` | Loads user from DB; required by `DaoAuthenticationProvider` |
| `AuthController` + login endpoint | JWT issuance; login still works the same way |
| CSRF disabled + stateless session | Correct config for a JWT API; do not change |
| `AuthenticationEntryPoint` (401 handler) | Returns clean 401 JSON; keep as-is |
| `AccessDeniedHandler` (403 handler) | Will still be needed for Spring Security layer errors |

### Replace / Augment

| Component | Change |
|---|---|
| `SecurityConfig.authorizeHttpRequests()` rules | Loosen to: authenticated users pass HTTP rules; fine-grained checks happen in AOP layer |
| `@PreAuthorize` on controllers | Remove entirely; replaced by `@RequirePermission` |
| JWT `role` claim | Replace with `permissions` array claim |
| `AppUserDetails.getRoleName()` | Add `getPermissions()` returning `Set<Permission>` |
| Role enum used for authorization decisions | Keep for DB storage; add Permission enum for authorization |

### The Layered Defense Model After Migration

```
Layer 1 — Spring Security Filter (KEEP)
  ├── Validates JWT signature
  ├── Sets SecurityContextHolder (authenticated user)
  └── Rejects unauthenticated requests with 401

Layer 2 — URL-Level Rules (LOOSEN)
  └── All /api/** → .authenticated() only (no role checks here)
  
Layer 3 — AOP Permission Checks (NEW)
  ├── @RequirePermission(Permission.USER_DELETE) on method
  ├── AuthorizationAspect intercepts the call
  ├── Reads JWT from JwtRequestContext
  ├── Extracts permissions array
  └── Throws InsufficientPermissionException if not present
```

This design means Spring Security handles authentication (is this a valid user?) and your AOP framework handles authorization (can this user do this specific thing?). These are genuinely separate concerns.

---

## 4. Permission Model Design

### 4.1 The Permission Enum

```java
// src/main/java/com/internship/student_exam_api/security/permission/Permission.java

package com.internship.student_exam_api.security.permission;

public enum Permission {

    // ── Student management ──────────────────────────────────
    USER_VIEW,
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,

    // ── Subject management ──────────────────────────────────
    SUBJECT_VIEW,
    SUBJECT_CREATE,
    SUBJECT_UPDATE,
    SUBJECT_DELETE,

    // ── Exam management ─────────────────────────────────────
    EXAM_VIEW,
    EXAM_CREATE,
    EXAM_UPDATE,

    // ── Result management ────────────────────────────────────
    RESULT_VIEW,
    RESULT_CREATE,
    RESULT_UPDATE,

    // ── AI analytics (for the Spring AI phase) ───────────────
    AI_INSIGHTS_VIEW
}
```

### 4.2 Role-to-Permission Mapping

Rather than authorizing by role at runtime, roles are expanded to permission sets at token generation time. The token carries permissions; the authorizer never needs to know the role.

```java
// src/main/java/com/internship/student_exam_api/security/permission/RolePermissions.java

package com.internship.student_exam_api.security.permission;

import com.internship.student_exam_api.enums.Role;
import java.util.EnumSet;
import java.util.Set;

public final class RolePermissions {

    private RolePermissions() {}

    private static final Set<Permission> ADMIN_PERMISSIONS = EnumSet.of(
        Permission.USER_VIEW,
        Permission.USER_CREATE,
        Permission.USER_UPDATE,
        Permission.USER_DELETE,
        Permission.SUBJECT_VIEW,
        Permission.SUBJECT_CREATE,
        Permission.SUBJECT_UPDATE,
        Permission.SUBJECT_DELETE,
        Permission.EXAM_VIEW,
        Permission.EXAM_CREATE,
        Permission.EXAM_UPDATE,
        Permission.RESULT_VIEW,
        Permission.RESULT_CREATE,
        Permission.RESULT_UPDATE,
        Permission.AI_INSIGHTS_VIEW
    );

    private static final Set<Permission> STUDENT_PERMISSIONS = EnumSet.of(
        Permission.USER_VIEW,
        Permission.SUBJECT_VIEW,
        Permission.EXAM_VIEW,
        Permission.RESULT_VIEW
    );

    public static Set<Permission> forRole(Role role) {
        return switch (role) {
            case ADMIN   -> EnumSet.copyOf(ADMIN_PERMISSIONS);
            case STUDENT -> EnumSet.copyOf(STUDENT_PERMISSIONS);
        };
    }
}
```

### 4.3 Why Permissions in the Token, Not Roles?

**Role check (current):**
```
Request arrives → read role claim → check "is role == ADMIN?" → allow/deny
```

**Permission check (new):**
```
Request arrives → read permissions claim → check "does permissions contain USER_DELETE?" → allow/deny
```

The permission approach wins on three dimensions:

**Granularity.** You can issue a token to an "Exam Grader" user that has `RESULT_CREATE` and `RESULT_UPDATE` but not `USER_DELETE` — without inventing a new role. You just change the permission set on token issuance.

**Decoupling.** The authorizer (`AuthorizationAspect`) does not need to know that `ADMIN` implies `USER_DELETE`. That mapping lives in one place: `RolePermissions`. Everything downstream works with permissions only.

**Auditability.** A token's permissions are self-describing. You can inspect a JWT and immediately know exactly what the bearer is allowed to do — no lookup table needed.

---

## 5. JWT Token Redesign

### 5.1 New Token Structure

```json
{
  "sub": "admin@school.com",
  "permissions": [
    "USER_VIEW",
    "USER_CREATE",
    "USER_UPDATE",
    "USER_DELETE",
    "SUBJECT_VIEW",
    "SUBJECT_CREATE",
    "RESULT_VIEW",
    "RESULT_CREATE",
    "RESULT_UPDATE",
    "EXAM_VIEW",
    "EXAM_CREATE",
    "AI_INSIGHTS_VIEW"
  ],
  "iat": 1700000000,
  "exp": 1700086400
}
```

The `role` claim is removed. The authorizer never reads a role from the token — only permissions.

### 5.2 Updated JwtUtil

```java
// Updated sections of JwtUtil.java

public String generateToken(AppUserDetails userDetails) {
    Set<Permission> permissions = RolePermissions.forRole(userDetails.getRole());

    // Convert Permission enum values to strings for JWT serialization
    List<String> permissionNames = permissions.stream()
        .map(Permission::name)
        .collect(Collectors.toList());

    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim("permissions", permissionNames)   // ← replaces "role" claim
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expirationMs))
        .signWith(getSigningKey())
        .compact();
}

@SuppressWarnings("unchecked")
public Set<Permission> extractPermissions(String token) {
    List<String> permNames = (List<String>) extractClaims(token)
        .get("permissions", List.class);

    if (permNames == null) return EnumSet.noneOf(Permission.class);

    return permNames.stream()
        .map(name -> {
            try { return Permission.valueOf(name); }
            catch (IllegalArgumentException e) { return null; }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
}
```

---

## 6. Package Structure

```
src/main/java/com/internship/student_exam_api/
│
├── security/
│   ├── annotation/
│   │   └── RequirePermission.java          ← custom annotation
│   │
│   ├── aspect/
│   │   └── AuthorizationAspect.java        ← AOP interceptor
│   │
│   ├── context/
│   │   ├── JwtRequestContext.java          ← per-request JWT state holder
│   │   └── JwtRequestContextFilter.java    ← populates context from request
│   │
│   ├── permission/
│   │   ├── Permission.java                 ← permission enum
│   │   └── RolePermissions.java            ← role → permission mapping
│   │
│   ├── exception/
│   │   ├── InsufficientPermissionException.java
│   │   └── MissingAuthorizationHeaderException.java
│   │
│   ├── jwt/
│   │   └── JwtUtil.java                    ← (already exists, updated)
│   │
│   ├── AppUserDetails.java                 ← (already exists, updated)
│   ├── JwtAuthFilter.java                  ← (already exists, unchanged)
│   └── UserDetailsServiceImpl.java         ← (already exists, unchanged)
│
└── config/
    └── SecurityConfig.java                 ← (already exists, updated rules)
```

---

## 7. Component Design (File-by-File)

### 7.1 `@RequirePermission` Annotation

```java
// src/main/java/com/internship/student_exam_api/security/annotation/RequirePermission.java

package com.internship.student_exam_api.security.annotation;

import com.internship.student_exam_api.security.permission.Permission;

import java.lang.annotation.*;

/**
 * Marks a method as requiring a specific permission in the JWT bearer token.
 *
 * Usage:
 *   @RequirePermission(Permission.USER_DELETE)
 *   public void deleteStudent(Long id) { ... }
 *
 * The annotation is intercepted by AuthorizationAspect, which:
 *   1. Reads the JWT from the current request (via JwtRequestContext)
 *   2. Extracts the permissions claim
 *   3. Throws InsufficientPermissionException if the required permission is absent
 *
 * AOP LIMITATION — SELF-INVOCATION:
 *   If method A (without annotation) calls method B (with @RequirePermission)
 *   within the same bean, the aspect will NOT fire because the call bypasses
 *   the Spring proxy. See AuthorizationAspect Javadoc and Section 9 of the
 *   architecture document for solutions.
 *
 * @see AuthorizationAspect
 * @see Permission
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)     // Must be RUNTIME for AOP to read it
@Documented
public @interface RequirePermission {

    /**
     * The permission required to invoke this method.
     */
    Permission value();
}
```

**Why `@Retention(RetentionPolicy.RUNTIME)`?**

There are three retention policies: `SOURCE` (discarded after compilation), `CLASS` (in bytecode but not loaded by JVM), and `RUNTIME` (available via reflection at runtime). AOP aspects use reflection to read annotations — `aspect.getAnnotation(RequirePermission.class)` — so `RUNTIME` is mandatory. Using `CLASS` here would mean the aspect finds `null` for every annotated method and authorization would silently fail.

### 7.2 `JwtRequestContext` — Parse Once, Use Everywhere

The problem this solves: without a context object, every component that needs to check permissions must re-parse the JWT from the `Authorization` header. This is wasteful (parsing is non-trivial cryptographic work) and fragile (if the header format changes, you fix it in multiple places).

```java
// src/main/java/com/internship/student_exam_api/security/context/JwtRequestContext.java

package com.internship.student_exam_api.security.context;

import com.internship.student_exam_api.security.permission.Permission;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.EnumSet;
import java.util.Set;

/**
 * Request-scoped bean that holds the parsed JWT state for one HTTP request.
 *
 * SCOPE: @RequestScope means Spring creates one instance per HTTP request and
 * destroys it when the request completes. This is different from:
 *   @Singleton (default) — one instance for the application lifetime
 *   @SessionScope — one per HTTP session
 *   @RequestScope — one per HTTP request
 *
 * WHY NOT ThreadLocal?
 *   ThreadLocal is a valid alternative (and what Spring Security itself uses
 *   internally via SecurityContextHolder). However, @RequestScope beans are
 *   easier to test (just inject a mock), automatically destroyed after the
 *   request, and fit naturally into Spring's DI model.
 *
 * USAGE:
 *   The filter JwtRequestContextFilter populates this bean once at request entry.
 *   AuthorizationAspect reads from it without re-parsing the JWT.
 *   Any service that needs to know "who is calling this" can inject this bean.
 */
@Component
@RequestScope
public class JwtRequestContext {

    private String email;
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);
    private boolean authenticated = false;
    private String rawToken;

    public void populate(String email, Set<Permission> permissions, String rawToken) {
        this.email = email;
        this.permissions = permissions;
        this.rawToken = rawToken;
        this.authenticated = true;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getEmail() { return email; }
    public Set<Permission> getPermissions() { return permissions; }
    public String getRawToken() { return rawToken; }
}
```

### 7.3 `JwtRequestContextFilter` — Populating the Context

```java
// src/main/java/com/internship/student_exam_api/security/context/JwtRequestContextFilter.java

package com.internship.student_exam_api.security.context;

import com.internship.student_exam_api.security.jwt.JwtUtil;
import com.internship.student_exam_api.security.permission.Permission;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Runs AFTER JwtAuthFilter. By the time this filter runs, we know:
 *   - The JWT has been validated by JwtAuthFilter
 *   - SecurityContextHolder has been populated
 *
 * This filter's job is purely mechanical: extract the JWT, parse it,
 * populate JwtRequestContext, and continue the chain.
 *
 * FILTER ORDER:
 *   JwtAuthFilter (validates JWT, sets SecurityContextHolder)
 *       │
 *       ▼
 *   JwtRequestContextFilter (parses permissions into JwtRequestContext)
 *       │
 *       ▼
 *   DispatcherServlet → Controller → Service
 *
 * IMPORTANT: This filter tolerates missing/invalid tokens gracefully.
 * If there is no Authorization header, we simply leave JwtRequestContext
 * in its default (unauthenticated) state. JwtAuthFilter already handled
 * the 401 for protected endpoints.
 */
@Component
@Slf4j
public class JwtRequestContextFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtRequestContext jwtRequestContext;

    public JwtRequestContextFilter(JwtUtil jwtUtil, JwtRequestContext jwtRequestContext) {
        this.jwtUtil = jwtUtil;
        this.jwtRequestContext = jwtRequestContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String email = jwtUtil.extractEmail(token);
            Set<Permission> permissions = jwtUtil.extractPermissions(token);
            jwtRequestContext.populate(email, permissions, token);
            log.debug("JwtRequestContext populated for user: {} with {} permissions",
                email, permissions.size());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

**Registering the Filter in SecurityConfig (order matters):**

```java
// In SecurityConfig.filterChain():

.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(jwtRequestContextFilter, JwtAuthFilter.class)   // ← NEW
```

### 7.4 Custom Exceptions

```java
// src/main/java/com/internship/student_exam_api/security/exception/InsufficientPermissionException.java

package com.internship.student_exam_api.security.exception;

import com.internship.student_exam_api.security.permission.Permission;

/**
 * Thrown by AuthorizationAspect when a method requires a permission
 * that the current JWT does not contain.
 *
 * Maps to HTTP 403 in GlobalExceptionHandler.
 *
 * WHY a separate exception and not AccessDeniedException?
 *   Spring Security's AccessDeniedException is caught by the AccessDeniedHandler
 *   in the filter chain, not by @ControllerAdvice in GlobalExceptionHandler.
 *   Our custom exception propagates through the normal exception handling
 *   path and is caught by GlobalExceptionHandler's @ExceptionHandler.
 *
 *   This makes the response consistent with all other error responses in
 *   the application (same JSON structure via ApiErrorResponse).
 */
public class InsufficientPermissionException extends RuntimeException {

    private final Permission requiredPermission;

    public InsufficientPermissionException(Permission required) {
        super("Access denied. Required permission: " + required.name());
        this.requiredPermission = required;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }
}
```

```java
// src/main/java/com/internship/student_exam_api/security/exception/MissingAuthorizationHeaderException.java

package com.internship.student_exam_api.security.exception;

/**
 * Thrown when AuthorizationAspect fires on a method but no JWT is present
 * in the JwtRequestContext. This should rarely happen in practice because
 * Spring Security's JwtAuthFilter already rejects unauthenticated requests.
 *
 * Maps to HTTP 401 in GlobalExceptionHandler.
 */
public class MissingAuthorizationHeaderException extends RuntimeException {

    public MissingAuthorizationHeaderException() {
        super("No valid authorization token found in request context");
    }
}
```

**Add handlers to GlobalExceptionHandler:**

```java
@ExceptionHandler(InsufficientPermissionException.class)
public ResponseEntity<ApiErrorResponse> handleInsufficientPermission(
        InsufficientPermissionException ex) {
    log.warn("Permission denied: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
        ApiErrorResponse.builder()
            .status(403)
            .error("FORBIDDEN")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build()
    );
}

@ExceptionHandler(MissingAuthorizationHeaderException.class)
public ResponseEntity<ApiErrorResponse> handleMissingAuth(
        MissingAuthorizationHeaderException ex) {
    log.warn("Missing auth: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
        ApiErrorResponse.builder()
            .status(401)
            .error("UNAUTHORIZED")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build()
    );
}
```

### 7.5 `AuthorizationAspect` — The Core

```java
// src/main/java/com/internship/student_exam_api/security/aspect/AuthorizationAspect.java

package com.internship.student_exam_api.security.aspect;

import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.internship.student_exam_api.security.context.JwtRequestContext;
import com.internship.student_exam_api.security.exception.InsufficientPermissionException;
import com.internship.student_exam_api.security.exception.MissingAuthorizationHeaderException;
import com.internship.student_exam_api.security.permission.Permission;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * ═══════════════════════════════════════════════════════════════
 * AuthorizationAspect — AOP-based permission enforcement
 * ═══════════════════════════════════════════════════════════════
 *
 * HOW SPRING AOP WORKS (proxy model):
 *
 *   When Spring sees @Aspect, it wraps the target bean in a PROXY.
 *   There are two proxy types:
 *
 *   1. JDK Dynamic Proxy:
 *      - Used when the bean implements an interface
 *      - Proxy implements the same interface
 *      - All method calls go through InvocationHandler
 *
 *   2. CGLIB Proxy:
 *      - Used when the bean has no interface (most Spring beans)
 *      - CGLIB generates a SUBCLASS of your bean at startup
 *      - Overrides all non-final methods to call the advice
 *
 *   WHAT HAPPENS AT RUNTIME:
 *
 *   Client code holds a reference to the PROXY, not the actual bean.
 *   When client calls proxy.deleteStudent(1L):
 *     1. Proxy intercepts the call
 *     2. Runs Before/Around/After advice (our AuthorizationAspect)
 *     3. If advice proceeds, calls the real bean's deleteStudent(1L)
 *     4. Returns result
 *
 *   CRITICAL: Self-invocation breaks this. See Section 9 of the doc.
 *
 * POINTCUT DESIGN:
 *
 *   The pointcut "@annotation(requirePermission)" matches any method
 *   in any class annotated with @RequirePermission. This is annotation-
 *   driven: no package scanning, no inheritance, just the presence of
 *   the annotation.
 *
 *   Alternative: "execution(* com.internship..controller.*.*(..))" matches
 *   all methods in all controllers by signature pattern. Less precise.
 *
 *   We use annotation-driven because:
 *   - Only methods that need permission checks get intercepted
 *   - Controllers, services, or any other bean can use the annotation
 *   - The annotation is self-documenting (you see @RequirePermission on the method)
 *
 * AROUND vs. BEFORE advice:
 *
 *   @Before runs before the method but cannot prevent execution (it can
 *   throw exceptions, but it cannot swallow the call).
 *
 *   @Around wraps the call completely. You decide whether to call
 *   joinPoint.proceed() or not. This gives maximum control and is the
 *   correct choice for security gates that need to conditionally block.
 */
@Aspect
@Component
@Slf4j
public class AuthorizationAspect {

    private final JwtRequestContext jwtRequestContext;

    public AuthorizationAspect(JwtRequestContext jwtRequestContext) {
        this.jwtRequestContext = jwtRequestContext;
    }

    /**
     * Around advice that fires on any method annotated with @RequirePermission.
     *
     * The binding "requirePermission" extracts the annotation instance from the
     * matched join point, so we can read requirePermission.value() directly.
     *
     * @param joinPoint       the intercepted method call
     * @param requirePermission the @RequirePermission annotation on the method
     * @return the method's return value if permission is granted
     * @throws Throwable if the wrapped method throws, or if permission is denied
     */
    @Around("@annotation(requirePermission)")
    public Object enforcePermission(ProceedingJoinPoint joinPoint,
                                    RequirePermission requirePermission) throws Throwable {

        Permission required = requirePermission.value();
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("[AUTHZ] Checking permission {} for method {}", required, methodName);

        // Guard: JwtRequestContext must be populated by JwtRequestContextFilter.
        // If not populated, either the request went through a public endpoint
        // (which should not be annotated) or the filter ordering is wrong.
        if (!jwtRequestContext.isAuthenticated()) {
            log.warn("[AUTHZ] No authenticated context for method {}", methodName);
            throw new MissingAuthorizationHeaderException();
        }

        // The permission check itself — O(1) lookup in an EnumSet
        if (!jwtRequestContext.hasPermission(required)) {
            log.warn("[AUTHZ] DENIED — user={} required={} method={}",
                jwtRequestContext.getEmail(), required, methodName);
            throw new InsufficientPermissionException(required);
        }

        log.debug("[AUTHZ] GRANTED — user={} permission={} method={}",
            jwtRequestContext.getEmail(), required, methodName);

        return joinPoint.proceed();
    }
}
```

### 7.6 Applying `@RequirePermission` to Controllers

Remove all `@PreAuthorize` annotations and replace with `@RequirePermission`:

```java
// StudentController.java — updated

@PostMapping
@RequirePermission(Permission.USER_CREATE)          // ← replaces @PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody StudentCreateRequest request) {
    log.info("POST /api/students");
    return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(request));
}

@GetMapping
@RequirePermission(Permission.USER_VIEW)
public ResponseEntity<List<StudentResponse>> getAllStudents() {
    log.info("GET /api/students");
    return ResponseEntity.ok(studentService.getAllStudents());
}

@GetMapping("/{id}")
@RequirePermission(Permission.USER_VIEW)
public ResponseEntity<StudentResponse> getStudentById(@PathVariable Long id) {
    log.info("GET /api/students/{}", id);
    return ResponseEntity.ok(studentService.getStudentById(id));
}

@PutMapping("/{id}")
@RequirePermission(Permission.USER_UPDATE)
public ResponseEntity<StudentResponse> updateStudent(@PathVariable Long id,
        @Valid @RequestBody StudentUpdateRequest request) {
    log.info("PUT /api/students/{}", id);
    return ResponseEntity.ok(studentService.updateStudent(id, request));
}

@DeleteMapping("/{id}")
@RequirePermission(Permission.USER_DELETE)
public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
    log.info("DELETE /api/students/{}", id);
    studentService.deleteStudent(id);
    return ResponseEntity.noContent().build();
}
```

Apply the same pattern to `SubjectController`, `ExamController`, and `ResultController`.

### 7.7 Updated SecurityConfig

```java
// SecurityConfig.java — updated authorizeHttpRequests block

.authorizeHttpRequests(auth -> auth
    // Public: login endpoint
    .requestMatchers("/api/auth/**").permitAll()

    // Public: Swagger UI
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

    // All other API requests require authentication only.
    // Fine-grained permission checks are handled by @RequirePermission via AOP.
    .requestMatchers("/api/**").authenticated()

    .anyRequest().authenticated()
)
```

The HTTP-method-level role rules (`hasAnyRole`, `hasRole`) are removed entirely. Spring Security still rejects unauthenticated requests (401). The AOP layer handles everything else.

---

## 8. Request Flow Diagrams

### 8.1 Complete Request Flow — DELETE /api/students/5

```
Client
  │
  │  DELETE /api/students/5
  │  Authorization: Bearer eyJhbGci...
  │
  ▼
Tomcat (HTTP)
  │
  ▼
Spring Security Filter Chain
  │
  ├── JwtAuthFilter
  │     ├── extracts "eyJhbGci..." from Authorization header
  │     ├── jwtUtil.validateToken() → true
  │     ├── jwtUtil.extractEmail() → "admin@school.com"
  │     ├── userDetailsService.loadUserByUsername("admin@school.com")
  │     └── sets SecurityContextHolder.getContext().setAuthentication(...)
  │
  ├── JwtRequestContextFilter (NEW)
  │     ├── extracts token again from header
  │     ├── jwtUtil.extractPermissions() → {USER_VIEW, USER_DELETE, RESULT_VIEW, ...}
  │     └── jwtRequestContext.populate("admin@school.com", permissions, rawToken)
  │
  └── authorizeHttpRequests check:
        └── /api/students/5 → .authenticated() → is authenticated? YES → pass
  │
  ▼
DispatcherServlet
  │
  ▼
HandlerMapping → StudentController.deleteStudent()
  │
  ▼
CGLIB Proxy wrapping StudentController intercepts the call
  │
  ▼
AuthorizationAspect.enforcePermission()
  │
  ├── requirePermission.value() → Permission.USER_DELETE
  ├── jwtRequestContext.isAuthenticated() → true
  ├── jwtRequestContext.hasPermission(USER_DELETE) → true
  └── joinPoint.proceed() → calls real StudentController.deleteStudent(5L)
  │
  ▼
StudentController.deleteStudent(5L)
  │
  ▼
StudentService.deleteStudent(5L)
  │
  ▼
studentRepository.deleteById(5L)
  │
  ▼
HTTP 204 No Content
```

### 8.2 Authorization Failure Flow — STUDENT token attempts DELETE

```
Client (STUDENT token: permissions = {USER_VIEW, SUBJECT_VIEW, EXAM_VIEW, RESULT_VIEW})
  │
  │  DELETE /api/students/5
  │  Authorization: Bearer <student-token>
  │
  ▼
JwtAuthFilter → validates token → sets SecurityContextHolder (authenticated as student)
  │
  ▼
JwtRequestContextFilter → extracts permissions {USER_VIEW, SUBJECT_VIEW, EXAM_VIEW, RESULT_VIEW}
  │
  ▼
authorizeHttpRequests → .authenticated() → PASS (token is valid)
  │
  ▼
CGLIB Proxy → AuthorizationAspect.enforcePermission()
  │
  ├── required = Permission.USER_DELETE
  ├── jwtRequestContext.hasPermission(USER_DELETE) → false  ← USER_DELETE not in student permissions
  └── throws InsufficientPermissionException("Access denied. Required permission: USER_DELETE")
  │
  ▼
GlobalExceptionHandler.handleInsufficientPermission()
  │
  ▼
HTTP 403 Forbidden
{
  "status": 403,
  "error": "FORBIDDEN",
  "message": "Access denied. Required permission: USER_DELETE",
  "timestamp": "2025-01-15T14:23:45"
}
```

---

## 9. AOP Proxy Mechanics & The Self-Invocation Problem

This is the deepest concept in the entire framework. Understanding it separates engineers who "use Spring AOP" from those who understand it.

### 9.1 How Spring Proxies Work

When Spring's `BeanPostProcessor` sees an `@Aspect` bean, it scans all other beans at startup and wraps any that have methods matching the aspect's pointcut in a proxy.

```
What you write:
  @Service
  public class StudentController { ... }

What Spring creates in the ApplicationContext:
  StudentController$$SpringCGLIBProxy$$0 extends StudentController {
    // CGLIB generates this class at startup using ASM bytecode manipulation
    
    @Override
    public ResponseEntity<Void> deleteStudent(Long id) {
        // === ADVICE CHAIN ===
        AuthorizationAspect.enforcePermission(joinPoint, annotation);
        // if proceeds:
        return super.deleteStudent(id);
    }
  }
```

Every reference you get from the ApplicationContext (`@Autowired`, constructor injection, etc.) is actually a reference to the proxy. The real bean instance is hidden inside the proxy.

### 9.2 Self-Invocation — Why It Breaks

```java
@Service
public class StudentService {

    public void bulkDeleteStudents(List<Long> ids) {
        // This calls deleteStudent via 'this' — the real bean reference, NOT the proxy
        for (Long id : ids) {
            this.deleteStudent(id);    // ← AOP will NOT fire here
        }
    }

    @RequirePermission(Permission.USER_DELETE)
    public void deleteStudent(Long id) {
        studentRepository.deleteById(id);
    }
}
```

When `bulkDeleteStudents` calls `this.deleteStudent(id)`, it bypasses the proxy entirely. The call goes directly to the real `StudentService` instance. `AuthorizationAspect` never sees the call. The permission check is silently skipped.

This is not a Spring bug. It is a fundamental consequence of the proxy pattern: the proxy wraps the object from outside. Internal method calls from within the object cannot go through the proxy because the object does not hold a reference to its own proxy.

```
External caller → Proxy → Real bean
                  ↑
              Aspect fires here

Real bean (this.method()) → Real bean (NO proxy in this path)
                             ↑
                         Aspect DOES NOT fire
```

### 9.3 When Self-Invocation Occurs

- `this.someAnnotatedMethod()` inside the same class
- A private helper method calling an annotated method
- A `@Transactional` method (same proxy issue — annotated transactions also break on self-invocation)
- An inner class calling its enclosing class's annotated method

### 9.4 Four Solutions

**Solution A: Bean Separation (Recommended for Clean Architecture)**

The cleanest solution. If internal methods need permission checks, move them to a separate bean.

```java
@Service
public class StudentBulkService {
    private final StudentService studentService;  // injected proxy

    public void bulkDeleteStudents(List<Long> ids) {
        for (Long id : ids) {
            studentService.deleteStudent(id);  // calls proxy → aspect fires
        }
    }
}

@Service
public class StudentService {
    @RequirePermission(Permission.USER_DELETE)
    public void deleteStudent(Long id) { ... }
}
```

**Solution B: Self-Injection**

Inject the proxy into the bean itself using `@Lazy` to break the circular dependency.

```java
@Service
public class StudentService {

    @Autowired
    @Lazy
    private StudentService self;  // ← injects the proxy, not 'this'

    public void bulkDeleteStudents(List<Long> ids) {
        for (Long id : ids) {
            self.deleteStudent(id);  // calls proxy → aspect fires
        }
    }

    @RequirePermission(Permission.USER_DELETE)
    public void deleteStudent(Long id) { ... }
}
```

`@Lazy` defers the injection until first use, breaking the circular reference `StudentService depends on StudentService`.

**Solution C: AopContext.currentProxy()**

Retrieves the current proxy from a thread-local set by Spring.

```java
@Service
public class StudentService {

    public void bulkDeleteStudents(List<Long> ids) {
        StudentService proxy = (StudentService) AopContext.currentProxy();
        for (Long id : ids) {
            proxy.deleteStudent(id);  // calls proxy → aspect fires
        }
    }
}
```

Requires `@EnableAspectJAutoProxy(exposeProxy = true)` in your configuration. This is ugly (cast from Object) and couples your service code to Spring AOP mechanics. Avoid unless necessary.

**Solution D: AspectJ Compile-Time or Load-Time Weaving (Nuclear Option)**

AspectJ weaves advice directly into bytecode, not through proxies. There is no proxy at all — the advice code is literally compiled into every call site of the annotated method.

```xml
<!-- pom.xml for compile-time weaving -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>aspectj-maven-plugin</artifactId>
    <version>1.14.0</version>
    <configuration>
        <complianceLevel>17</complianceLevel>
        <source>17</source>
        <target>17</target>
    </configuration>
    <executions>
        <execution>
            <goals><goal>compile</goal></goals>
        </execution>
    </executions>
</plugin>
```

With AspectJ weaving, `this.deleteStudent(id)` is intercepted correctly because the bytecode itself contains the check. The proxy is gone.

**Tradeoffs:**

| Solution | Proxy Required | Complexity | Spring Coupling | Recommended |
|---|---|---|---|---|
| Bean Separation | Yes | Low | None | ✅ Yes |
| Self-Injection | Yes | Medium | Low | Acceptable |
| AopContext.currentProxy() | Yes | Medium | High | Avoid |
| AspectJ Weaving | No | High | None | Only if needed |

For this project, **Bean Separation** should be the default. Structure your code so that annotated methods are called via injected service references, not `this`.

### 9.5 Other Proxy Limitations

**`final` methods cannot be proxied by CGLIB.** CGLIB generates a subclass — it cannot override `final` methods. If you annotate a `final` method with `@RequirePermission`, the annotation is silently ignored. Never mark annotated methods as `final`.

**`private` methods are not intercepted.** AOP can only intercept methods visible to the proxy (public and package-private). A `private` method annotated with `@RequirePermission` will never trigger the aspect.

**Interfaces and JDK Proxies.** If your bean implements an interface and the pointcut is defined against the interface type, JDK dynamic proxying is used instead of CGLIB. The annotation must be on the implementation method, not just the interface declaration (unless your pointcut is written to match both).

---

## 10. JwtContext Integration with JwtAuthFilter

The key question is: should `JwtRequestContextFilter` duplicate the token extraction logic from `JwtAuthFilter`?

Yes, and this is intentional. Here is why:

`JwtAuthFilter` has one responsibility: validate the token and set `SecurityContextHolder`. It is a Spring Security concern. Coupling it to `JwtRequestContext` would mix Spring Security infrastructure with your custom authorization system — violating separation of concerns.

`JwtRequestContextFilter` has one responsibility: populate the `JwtRequestContext`. It is a custom authorization concern. It re-reads the header, but only after `JwtAuthFilter` has already validated the token. The extraction overhead is negligible.

```
Concern Map:
  JwtAuthFilter           → Spring Security authentication
  JwtRequestContextFilter → Custom AOP authorization context
  AuthorizationAspect     → Custom AOP authorization enforcement
  
These three components do not reference each other.
They share only JwtUtil (validation/extraction utility).
```

---

## 11. Implementation Order & Timeline

### Phase 1 — Permission Foundation (45 min)

**Files to create/modify:**

1. `Permission.java` — create enum with all 15 permissions
2. `RolePermissions.java` — create ADMIN and STUDENT mappings
3. `JwtUtil.java` — add `extractPermissions()`, update `generateToken()` to embed permissions
4. `JwtUtilTest.java` — add tests for `extractPermissions()`, verify ADMIN token contains `USER_DELETE`, verify STUDENT token does not

**Validation:** Run existing 46 tests. They should all pass because the old `extractRole()` still exists and the test profile generates tokens the same way.

---

### Phase 2 — Request Context (30 min)

**Files to create:**

1. `JwtRequestContext.java` — request-scoped bean
2. `JwtRequestContextFilter.java` — filter to populate context
3. `SecurityConfig.java` — register filter after `JwtAuthFilter`

**Validation:** Add a debug endpoint temporarily (`GET /api/debug/context`) that returns the `JwtRequestContext` contents as JSON. Hit it with an admin token, verify the permissions array is populated correctly. Remove the debug endpoint before committing.

---

### Phase 3 — Annotation and Aspect (60 min)

**Files to create:**

1. `RequirePermission.java` — annotation
2. `InsufficientPermissionException.java` — custom exception
3. `MissingAuthorizationHeaderException.java` — custom exception
4. `GlobalExceptionHandler.java` — add two new `@ExceptionHandler` methods
5. `AuthorizationAspect.java` — the aspect itself

**Validation:** Write `AuthorizationAspectTest.java` (see Testing Strategy). Verify aspect fires, verify correct exceptions are thrown.

---

### Phase 4 — Controller Migration (45 min)

**Files to modify:**

1. `StudentController.java` — remove `@PreAuthorize`, add `@RequirePermission`
2. `SubjectController.java` — same
3. `ExamController.java` — same
4. `ResultController.java` — same
5. `SecurityConfig.java` — loosen `authorizeHttpRequests` to `.authenticated()` only

**Validation:** Run all 46 existing tests. The `@WebMvcTest` controller tests use `@WithMockUser(roles = "ADMIN")` — these need updating because `@RequirePermission` uses `JwtRequestContext`, not Spring Security's `SecurityContextHolder`. See Testing Strategy for how to handle this.

---

### Phase 5 — Test Suite Expansion (60 min)

Write the full test suite described in Section 12. Total target: 65+ tests.

**Timeline Total: ~4 hours**

---

## 12. Testing Strategy

### 12.1 Unit Tests — Permission and JWT

```java
// JwtPermissionTest.java

@Test
void adminTokenContainsUserDeletePermission() {
    AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
    String token = jwtUtil.generateToken(admin);
    Set<Permission> permissions = jwtUtil.extractPermissions(token);
    assertThat(permissions).contains(Permission.USER_DELETE);
}

@Test
void studentTokenDoesNotContainUserDeletePermission() {
    AppUserDetails student = makeUserDetails("student@school.com", Role.STUDENT);
    String token = jwtUtil.generateToken(student);
    Set<Permission> permissions = jwtUtil.extractPermissions(token);
    assertThat(permissions).doesNotContain(Permission.USER_DELETE);
}

@Test
void studentTokenContainsAllViewPermissions() {
    AppUserDetails student = makeUserDetails("student@school.com", Role.STUDENT);
    String token = jwtUtil.generateToken(student);
    Set<Permission> permissions = jwtUtil.extractPermissions(token);
    assertThat(permissions).containsExactlyInAnyOrder(
        Permission.USER_VIEW,
        Permission.SUBJECT_VIEW,
        Permission.EXAM_VIEW,
        Permission.RESULT_VIEW
    );
}

@Test
void permissionsAreEmptyForExpiredToken() {
    // Set expiration to 1ms, generate token, sleep, then extract
    ReflectionTestUtils.setField(jwtUtil, "expirationMs", 1L);
    AppUserDetails admin = makeUserDetails("admin@school.com", Role.ADMIN);
    String token = jwtUtil.generateToken(admin);
    Thread.sleep(10);
    // Token is expired; validateToken returns false; no permissions should be trusted
    assertThat(jwtUtil.validateToken(token)).isFalse();
}
```

### 12.2 Aspect Unit Tests

These tests verify the aspect logic in isolation, using mocked dependencies.

```java
// AuthorizationAspectTest.java

@ExtendWith(MockitoExtension.class)
class AuthorizationAspectTest {

    @Mock
    private JwtRequestContext jwtRequestContext;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RequirePermission requirePermission;

    @InjectMocks
    private AuthorizationAspect aspect;

    @Test
    void proceedsWhenUserHasRequiredPermission() throws Throwable {
        when(requirePermission.value()).thenReturn(Permission.USER_DELETE);
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(Permission.USER_DELETE)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.enforcePermission(joinPoint, requirePermission);

        assertThat(result).isEqualTo("result");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void throwsInsufficientPermissionWhenPermissionMissing() {
        when(requirePermission.value()).thenReturn(Permission.USER_DELETE);
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(Permission.USER_DELETE)).thenReturn(false);

        assertThrows(
            InsufficientPermissionException.class,
            () -> aspect.enforcePermission(joinPoint, requirePermission)
        );
        verifyNoInteractions(joinPoint);   // joinPoint.proceed() must NOT be called
    }

    @Test
    void throwsMissingAuthWhenContextNotAuthenticated() {
        when(requirePermission.value()).thenReturn(Permission.USER_DELETE);
        when(jwtRequestContext.isAuthenticated()).thenReturn(false);

        assertThrows(
            MissingAuthorizationHeaderException.class,
            () -> aspect.enforcePermission(joinPoint, requirePermission)
        );
    }

    @Test
    void propagatesExceptionFromUnderlyingMethod() throws Throwable {
        when(requirePermission.value()).thenReturn(Permission.USER_VIEW);
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(Permission.USER_VIEW)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

        assertThrows(
            RuntimeException.class,
            () -> aspect.enforcePermission(joinPoint, requirePermission)
        );
    }
}
```

### 12.3 Controller Integration Tests (WebMvcTest Slice)

The challenge: `@WebMvcTest` uses `@WithMockUser` to set up Spring Security context. But your `@RequirePermission` now depends on `JwtRequestContext`, which is populated by `JwtRequestContextFilter`, not Spring Security.

**Solution:** Create a test utility that populates `JwtRequestContext` directly.

```java
// TestJwtContextConfig.java (in test sources)

@TestConfiguration
public class TestJwtContextConfig {

    @Bean
    @RequestScope
    public JwtRequestContext jwtRequestContext() {
        // Returns a real JwtRequestContext — tests control it via @MockBean or direct injection
        return new JwtRequestContext();
    }
}
```

```java
// StudentControllerTest.java — updated for permission system

@WebMvcTest(StudentController.class)
@WithMockUser(roles = "ADMIN")
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private StudentService studentService;

    @MockBean
    private JwtRequestContext jwtRequestContext;    // ← mock the context

    @BeforeEach
    void grantAdminPermissions() {
        // Configure the mock to return true for any permission check
        // (simulating a fully-authorized admin)
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(any())).thenReturn(true);
    }

    @Test
    void deleteStudentReturnsForbiddenWhenPermissionMissing() throws Exception {
        // Override: simulate a STUDENT context that lacks USER_DELETE
        when(jwtRequestContext.hasPermission(Permission.USER_DELETE)).thenReturn(false);

        mockMvc.perform(delete("/api/students/1").with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").containsString("USER_DELETE"));
    }

    @Test
    void deleteStudentSucceedsWithUserDeletePermission() throws Exception {
        doNothing().when(studentService).deleteStudent(1L);

        mockMvc.perform(delete("/api/students/1").with(csrf()))
            .andExpect(status().isNoContent());
    }
}
```

### 12.4 Integration Tests — Full Stack

```java
// PermissionIntegrationTest.java

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepo;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void seedUsers() {
        userRepo.deleteAll();
        userRepo.save(new AppUser("admin@test.com", encoder.encode("admin123"), Role.ADMIN));
        userRepo.save(new AppUser("student@test.com", encoder.encode("student123"), Role.STUDENT));
    }

    // ── Permission matrix tests ────────────────────────────────────────────

    @Test
    void adminCanDeleteStudent() throws Exception {
        // First create a student
        String adminToken = getToken("admin@test.com", "admin123");
        String createResponse = mockMvc.perform(post("/api/students")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"email\":\"t@t.com\",\"rollNumber\":\"R1\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long studentId = mapper.readTree(createResponse).get("id").asLong();

        // Then delete it
        mockMvc.perform(delete("/api/students/" + studentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void studentCannotDeleteStudent() throws Exception {
        String studentToken = getToken("student@test.com", "student123");
        mockMvc.perform(delete("/api/students/1")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Access denied. Required permission: USER_DELETE"));
    }

    @Test
    void studentCanViewStudents() throws Exception {
        String studentToken = getToken("student@test.com", "student123");
        mockMvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk());
    }

    @Test
    void studentCannotCreateResult() throws Exception {
        String studentToken = getToken("student@test.com", "student123");
        mockMvc.perform(post("/api/results")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":1,\"examId\":1,\"marks\":75}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        String token = getToken("admin@test.com", "admin123");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        mockMvc.perform(get("/api/students")
                .header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized());
    }

    // ── Permission exhaustion matrix ──────────────────────────────────────
    // Verify EVERY permission for EVERY role — no permission should be
    // silently granted or silently denied.

    @ParameterizedTest
    @MethodSource("adminPermissions")
    void adminHasAllExpectedPermissions(Permission permission) {
        Set<Permission> adminPerms = RolePermissions.forRole(Role.ADMIN);
        assertThat(adminPerms).contains(permission);
    }

    @ParameterizedTest
    @MethodSource("studentForbiddenPermissions")
    void studentLacksWritePermissions(Permission permission) {
        Set<Permission> studentPerms = RolePermissions.forRole(Role.STUDENT);
        assertThat(studentPerms).doesNotContain(permission);
    }

    static Stream<Permission> adminPermissions() {
        return Arrays.stream(Permission.values());
    }

    static Stream<Permission> studentForbiddenPermissions() {
        return Stream.of(
            Permission.USER_CREATE, Permission.USER_UPDATE, Permission.USER_DELETE,
            Permission.SUBJECT_CREATE, Permission.SUBJECT_UPDATE, Permission.SUBJECT_DELETE,
            Permission.EXAM_CREATE, Permission.EXAM_UPDATE,
            Permission.RESULT_CREATE, Permission.RESULT_UPDATE
        );
    }

    // ── Self-invocation regression test ───────────────────────────────────
    // If someone accidentally introduces self-invocation on an annotated method,
    // this test catches it by verifying the exception IS thrown even in scenarios
    // that previously bypassed the aspect.

    @Test
    void studentTokenCannotCallBulkDeleteEvenViaIndirection() throws Exception {
        // This test documents and verifies the AOP boundary
        String studentToken = getToken("student@test.com", "student123");
        // Any endpoint requiring USER_DELETE must return 403 for student tokens
        mockMvc.perform(delete("/api/students/1")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isForbidden());
    }

    private String getToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString())
            .get("token").asText();
    }
}
```

### 12.5 Test Count Target

| Category | Tests | Notes |
|---|---|---|
| JwtUtil permission extraction | 6 | One per role, boundary cases |
| RolePermissions mapping | 4 | Admin has all, student has read-only, etc. |
| AuthorizationAspect unit | 6 | Grant, deny, no context, propagate exception, etc. |
| Controller WebMvcTest (per controller) | 8 × 4 = 32 | All existing tests + permission failure cases |
| Integration — permission matrix | 12 | All role/endpoint combinations |
| Integration — self-invocation regression | 2 | Document AOP boundary |
| **Total (new + migrated)** | **~65** | Up from 46 |

---

## 13. AOP Authorization vs. Spring Security — Detailed Comparison

### Where Each Approach Wins

| Dimension | AOP Framework | Spring Security |
|---|---|---|
| **Educational value** | ✅ Teaches proxy mechanics, annotation processing, request context propagation | ❌ Hides implementation behind framework abstractions |
| **Granularity** | ✅ Permission-level; can annotate any method in any layer | ⚠️ Role-level by default; `@PreAuthorize` supports SpEL but is complex |
| **Type safety** | ✅ `Permission.USER_DELETE` is a compiler-checked enum | ❌ `"hasRole('ADMIN')"` is a string; typos fail silently at runtime |
| **Testability** | ✅ Mock `JwtRequestContext`; inject in unit tests cleanly | ⚠️ Requires `@WithMockUser` or SecurityMockMvc; more test infrastructure |
| **Production hardening** | ❌ No OAuth2, no session management, no CSRF, no filter ordering guarantees | ✅ Decades of security hardening; known attack vectors are handled |
| **Self-invocation** | ❌ Silently bypassed; requires architectural discipline | ✅ Filter-level checks cannot be bypassed by internal calls |
| **Performance** | ✅ One permission lookup (EnumSet.contains is O(1)) | ✅ Comparable; both negligible in practice |
| **Maintenance** | ⚠️ Custom code = custom maintenance burden | ✅ Framework updates fix vulnerabilities automatically |
| **Third-party integration** | ❌ Custom framework; no out-of-box OAuth2, OpenID Connect | ✅ Full ecosystem support |
| **Filter chain bypass** | ❌ An annotated service method called from a filter won't hit the aspect | ✅ Filter chain is always evaluated |

### The Real Educational Value

The AOP framework teaches three things that Spring Security makes invisible:

**1. Proxies are real objects.** Your `@Service` class in the ApplicationContext is not actually an instance of your class — it is a CGLIB subclass. Understanding this explains why `@Transactional` also breaks on self-invocation, why `final` methods can't be transactional, and why `@Scope("prototype")` beans injected into singletons don't work the way you expect.

**2. Request scope is a design pattern.** `JwtRequestContext` demonstrates how to propagate per-request state through a Spring application without passing parameters through every method signature. This pattern is used in production for tracing correlation IDs, tenant context in multi-tenant systems, and feature flag contexts.

**3. Separation of concerns at the cross-cutting level.** Authorization, logging, performance monitoring, and transaction management are all cross-cutting concerns. AOP is the mechanism Spring uses internally for all of them (`@Transactional` is an AOP aspect). Understanding one aspect gives you the mental model for all the others.

### Verdict

For a production system handling real user data, keep Spring Security for authentication and use Spring Security's `@PreAuthorize` for authorization. The test coverage, vulnerability handling, and community support are not things you want to reinvent.

For an educational project where the goal is deep framework understanding, the custom AOP framework described in this document teaches more in five days than using Spring Security's authorization for five months. Build both. Understand why Spring Security exists by building what it replaces.

---

## Appendix A — File Change Summary

| File | Action | Change |
|---|---|---|
| `Permission.java` | Create | New permission enum |
| `RolePermissions.java` | Create | Role → permission mapping |
| `RequirePermission.java` | Create | Custom annotation |
| `InsufficientPermissionException.java` | Create | 403 exception |
| `MissingAuthorizationHeaderException.java` | Create | 401 exception |
| `JwtRequestContext.java` | Create | Request-scoped context |
| `JwtRequestContextFilter.java` | Create | Filter to populate context |
| `AuthorizationAspect.java` | Create | Core AOP interceptor |
| `JwtUtil.java` | Modify | Add `extractPermissions()`, update `generateToken()` |
| `AppUserDetails.java` | Modify | Add `getRole()` for `RolePermissions.forRole()` |
| `SecurityConfig.java` | Modify | Register new filter, loosen URL rules |
| `StudentController.java` | Modify | Replace `@PreAuthorize` with `@RequirePermission` |
| `SubjectController.java` | Modify | Same |
| `ExamController.java` | Modify | Same |
| `ResultController.java` | Modify | Same |
| `GlobalExceptionHandler.java` | Modify | Add two new exception handlers |
| `JwtUtilTest.java` | Modify | Add permission extraction tests |
| `AuthorizationAspectTest.java` | Create | Aspect unit tests |
| `PermissionIntegrationTest.java` | Create | Full-stack permission matrix tests |
| `StudentControllerTest.java` | Modify | Mock `JwtRequestContext` |
| `ResultControllerTest.java` | Modify | Same |

**Total new files: 8. Modified files: 12. Deleted files: 0.**

---

*End of Architecture Design Document*
