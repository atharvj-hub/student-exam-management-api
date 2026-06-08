package com.internship.student_exam_api.enums;

/**
 * Application roles for RBAC.
 *
 * <p>ADMIN   → full read + write access to all endpoints (create, update, delete)
 * <p>STUDENT → read-only access to all GET endpoints
 *
 * <p>Spring Security stores these as GrantedAuthority strings with the "ROLE_" prefix.
 * e.g., ADMIN becomes ROLE_ADMIN, STUDENT becomes ROLE_STUDENT.
 * hasRole("ADMIN") in @PreAuthorize checks for "ROLE_ADMIN" automatically.
 */
public enum Role {
    ADMIN,
    STUDENT
}
