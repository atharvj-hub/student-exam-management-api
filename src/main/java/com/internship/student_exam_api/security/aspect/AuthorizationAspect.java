package com.internship.student_exam_api.security.aspect;

import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.internship.student_exam_api.security.context.JwtRequestContext;
import com.internship.student_exam_api.security.exception.InsufficientPermissionException;
import com.internship.student_exam_api.security.exception.MissingAuthorizationHeaderException;
import com.internship.student_exam_api.security.permission.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthorizationAspect {

    private final JwtRequestContext jwtRequestContext;

    @Around("@annotation(requirePermission)")
    public Object enforcePermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        Permission required = requirePermission.value();
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("[AUTHZ] Checking permission {} for method {}", required, methodName);

        if (!jwtRequestContext.isAuthenticated()) {
            log.warn("[AUTHZ] No authenticated context for method {}", methodName);
            throw new MissingAuthorizationHeaderException();
        }

        if (!jwtRequestContext.hasPermission(required)) {
            log.warn("[AUTHZ] DENIED — user={} required={} method={}",
                jwtRequestContext.getEmail(), required, methodName);
            throw new InsufficientPermissionException(required);
        }

        log.debug("[AUTHZ] GRANTED — user={} permission={} method={}",
            jwtRequestContext.getEmail(), required, methodName);
        
        return joinPoint.proceed();
    }
}
