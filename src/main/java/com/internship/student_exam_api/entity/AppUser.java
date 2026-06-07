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
