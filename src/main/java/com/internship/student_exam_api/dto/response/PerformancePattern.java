package com.internship.student_exam_api.dto.response;

import com.internship.student_exam_api.enums.PerformanceTrend;
import com.internship.student_exam_api.enums.TrendScope;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PerformancePattern(
        PerformanceTrend type,
        
        TrendScope scope,
        
        @Size(max = 300, message = "Description must be max 300 characters.")
        String description,
        
        List<String> subjectsAffected
) {}
