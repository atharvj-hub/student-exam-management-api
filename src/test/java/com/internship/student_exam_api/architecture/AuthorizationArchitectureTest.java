package com.internship.student_exam_api.architecture;

import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

@AnalyzeClasses(packages = "com.internship.student_exam_api.controller")
public class AuthorizationArchitectureTest {

    @ArchTest
    public static final ArchRule controllers_must_have_require_permission = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .and().areDeclaredInClassesThat().doNotHaveSimpleName("AuthController")
            .should().beAnnotatedWith(RequirePermission.class)
            .because("All endpoints except AuthController must specify required permissions via the custom AOP framework.");
}
