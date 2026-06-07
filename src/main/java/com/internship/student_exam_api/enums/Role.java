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
