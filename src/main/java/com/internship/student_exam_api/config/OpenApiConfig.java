package com.internship.student_exam_api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Student Exam Result API",
        version = "1.0.0",
        description = "REST API for managing students, subjects, exams, and auto-calculated exam results."
    )
)
public class OpenApiConfig {
}
