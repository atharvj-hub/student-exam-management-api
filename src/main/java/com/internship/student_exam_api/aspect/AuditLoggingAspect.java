package com.internship.student_exam_api.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Aspect class to handle audit logging of all controller calls.
 * Implements cross-cutting logging concerns for security audits.
 */
@Aspect
@Component
@Slf4j
public class AuditLoggingAspect {

    /**
     * Intercepts and logs all controller method calls, measuring execution time
     * and logging credentials of the calling identity.
     *
     * @param joinPoint the AOP joint point representing the method execution
     * @return return value of the wrapped controller method
     * @throws Throwable if method execution fails
     */
    @Around("execution(* com.internship.student_exam_api.controller.*.*(..))")
    public Object auditControllerCall(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = "anonymous";
        String role  = "NONE";

        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {
            email = authentication.getName();
            role = authentication.getAuthorities().isEmpty()
                ? "NONE"
                : authentication.getAuthorities().iterator().next().getAuthority();
        }

        String method = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AUDIT] user={} role={} method={} duration={}ms status=SUCCESS",
                email, role, method, duration);

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[AUDIT] user={} role={} method={} duration={}ms status=ERROR error={}",
                email, role, method, duration, e.getClass().getSimpleName());

            throw e;
        }
    }
}
