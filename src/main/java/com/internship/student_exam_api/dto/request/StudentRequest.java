package com.internship.student_exam_api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ═══════════════════════════════════════════════════════════════
 * WHY DTOs (Data Transfer Objects) AND NOT RAW ENTITIES?
 * ═══════════════════════════════════════════════════════════════
 *
 * PROBLEM with exposing raw @Entity over HTTP:
 *
 * 1. OVER-POSTING (Mass Assignment Attack):
 *    If Student entity has an "id" field and you accept @RequestBody Student,
 *    an attacker can POST {"id": 999, "name": "Hacker"} and potentially
 *    overwrite another user's record (or bypass GeneratedValue).
 *
 * 2. INTERNAL LEAKAGE:
 *    Your entity might have "createdAt", "updatedAt", audit fields,
 *    or sensitive fields you don't want exposed to API consumers.
 *
 * 3. HIBERNATE SESSION PROBLEM:
 *    Jackson (JSON serializer) might try to serialize a LAZY-loaded
 *    relationship AFTER the Hibernate session closes.
 *    → LazyInitializationException on serialization.
 *    DTOs are plain Java objects — no Hibernate involvement.
 *
 * 4. SCHEMA COUPLING:
 *    If you expose entities directly, changing your DB schema
 *    (rename a column, add a field) breaks your API contract.
 *    DTOs decouple the API contract from the DB schema.
 *
 * PATTERN:
 *   Request DTO  → what the API ACCEPTS  (input validation here)
 *   Response DTO → what the API RETURNS  (only the fields clients need)
 *
 * The Service layer is responsible for converting:
 *   RequestDTO → Entity  (before saving)
 *   Entity     → ResponseDTO (before returning)
 */
@Getter
@Setter
@NoArgsConstructor
public class StudentRequest {

    /**
     * @NotBlank validates at the Java layer when @Valid is used on the controller parameter.
     * This fires BEFORE the Service is even called.
     * If blank: MethodArgumentNotValidException → our GlobalExceptionHandler catches it → 422.
     */
        @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Roll number is required")
    private String rollNumber;

    private String collegeName;
}
