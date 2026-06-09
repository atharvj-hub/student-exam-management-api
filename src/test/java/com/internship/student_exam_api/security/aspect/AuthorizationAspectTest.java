package com.internship.student_exam_api.security.aspect;

import com.internship.student_exam_api.security.annotation.RequirePermission;
import com.internship.student_exam_api.security.context.JwtRequestContext;
import com.internship.student_exam_api.security.exception.InsufficientPermissionException;
import com.internship.student_exam_api.security.exception.MissingAuthorizationHeaderException;
import com.internship.student_exam_api.security.permission.Permission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationAspectTest {

    @Mock
    private JwtRequestContext jwtRequestContext;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RequirePermission requirePermission;

    @Mock
    private Signature signature;

    @InjectMocks
    private AuthorizationAspect authorizationAspect;

    @BeforeEach
    void setUp() {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("TestClass.testMethod()");
    }

    @Test
    void proceedsWhenUserHasRequiredPermission() throws Throwable {
        when(requirePermission.value()).thenReturn(Permission.USER_VIEW);
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(Permission.USER_VIEW)).thenReturn(true);
        when(jwtRequestContext.getEmail()).thenReturn("test@test.com");
        
        Object expectedResponse = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResponse);

        Object actualResponse = authorizationAspect.enforcePermission(joinPoint, requirePermission);

        assertEquals(expectedResponse, actualResponse);
        verify(joinPoint).proceed();
    }

    @Test
    void throwsInsufficientPermissionWhenPermissionMissing() throws Throwable {
        when(requirePermission.value()).thenReturn(Permission.USER_DELETE);
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(Permission.USER_DELETE)).thenReturn(false);
        when(jwtRequestContext.getEmail()).thenReturn("test@test.com");

        InsufficientPermissionException thrown = assertThrows(InsufficientPermissionException.class, 
            () -> authorizationAspect.enforcePermission(joinPoint, requirePermission));

        assertEquals(Permission.USER_DELETE, thrown.getRequiredPermission());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void throwsMissingAuthWhenContextNotAuthenticated() throws Throwable {
        when(requirePermission.value()).thenReturn(Permission.USER_VIEW);
        when(jwtRequestContext.isAuthenticated()).thenReturn(false);

        assertThrows(MissingAuthorizationHeaderException.class, 
            () -> authorizationAspect.enforcePermission(joinPoint, requirePermission));

        verify(joinPoint, never()).proceed();
    }

    @Test
    void propagatesExceptionFromUnderlyingMethod() throws Throwable {
        when(requirePermission.value()).thenReturn(Permission.USER_VIEW);
        when(jwtRequestContext.isAuthenticated()).thenReturn(true);
        when(jwtRequestContext.hasPermission(Permission.USER_VIEW)).thenReturn(true);
        when(jwtRequestContext.getEmail()).thenReturn("test@test.com");
        
        RuntimeException expectedException = new RuntimeException("Underlying failure");
        when(joinPoint.proceed()).thenThrow(expectedException);

        RuntimeException thrown = assertThrows(RuntimeException.class, 
            () -> authorizationAspect.enforcePermission(joinPoint, requirePermission));
            
        assertEquals("Underlying failure", thrown.getMessage());
        verify(joinPoint).proceed();
    }
}
