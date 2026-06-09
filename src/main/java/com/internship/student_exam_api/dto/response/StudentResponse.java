package com.internship.student_exam_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * StudentResponse — what the API returns to clients.
 *
 * Notice: we include createdAt/updatedAt for audit purposes.
 * We do NOT include internal Hibernate-related fields.
 *
 * @Builder → Lombok generates a builder pattern:
 *   StudentResponse.builder()
 *       .id(student.getId())
 *       .name(student.getName())
 *       .build();
 *
 *   This is cleaner than setters and prevents partially-constructed objects.
 *   Once build() is called, the object is complete and immutable (if using @Value).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponse {
    private Long id;
    private String name;
    private String email;
    private String rollNumber;
    private String section;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
