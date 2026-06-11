package com.internship.student_exam_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "ai_analysis_cache",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_cache_student_hash",
        columnNames = {"student_id", "data_hash"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysisCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "data_hash", nullable = false, length = 64)
    private String dataHash;

    @Column(name = "model_used", nullable = false, length = 100)
    private String modelUsed;

    /** JSON-serialized StudentInsightPayload. */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
