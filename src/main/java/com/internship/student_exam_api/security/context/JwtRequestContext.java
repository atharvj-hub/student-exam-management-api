package com.internship.student_exam_api.security.context;

import com.internship.student_exam_api.security.permission.Permission;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.EnumSet;
import java.util.Set;

/**
 * Request-scoped bean that holds the parsed JWT state for one HTTP request.
 *
 * SCOPE: @RequestScope means Spring creates one instance per HTTP request and
 * destroys it when the request completes. This is different from:
 *   @Singleton (default) — one instance for the application lifetime
 *   @SessionScope — one per HTTP session
 *   @RequestScope — one per HTTP request
 *
 * WHY NOT ThreadLocal?
 *   ThreadLocal is a valid alternative (and what Spring Security itself uses
 *   internally via SecurityContextHolder). However, @RequestScope beans are
 *   easier to test (just inject a mock), automatically destroyed after the
 *   request, and fit naturally into Spring's DI model.
 *
 * USAGE:
 *   The filter JwtRequestContextFilter populates this bean once at request entry.
 *   AuthorizationAspect reads from it without re-parsing the JWT.
 *   Any service that needs to know "who is calling this" can inject this bean.
 */
@Component
@RequestScope
public class JwtRequestContext {

    private String email;
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);
    private boolean authenticated = false;
    private String rawToken;

    public void populate(String email, Set<Permission> permissions, String rawToken) {
        this.email = email;
        this.permissions = permissions;
        this.rawToken = rawToken;
        this.authenticated = true;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getEmail() { return email; }
    public Set<Permission> getPermissions() { return permissions; }
    public String getRawToken() { return rawToken; }
}
