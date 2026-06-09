package com.internship.student_exam_api.security.context;

import com.internship.student_exam_api.security.JwtUtil;
import com.internship.student_exam_api.security.permission.Permission;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Runs AFTER JwtAuthFilter. By the time this filter runs, we know:
 *   - The JWT has been validated by JwtAuthFilter
 *   - SecurityContextHolder has been populated
 *
 * This filter's job is purely mechanical: extract the JWT, parse it,
 * populate JwtRequestContext, and continue the chain.
 *
 * FILTER ORDER:
 *   JwtAuthFilter (validates JWT, sets SecurityContextHolder)
 *       │
 *       ▼
 *   JwtRequestContextFilter (parses permissions into JwtRequestContext)
 *       │
 *       ▼
 *   DispatcherServlet → Controller → Service
 *
 * IMPORTANT: This filter tolerates missing/invalid tokens gracefully.
 * If there is no Authorization header, we simply leave JwtRequestContext
 * in its default (unauthenticated) state. JwtAuthFilter already handled
 * the 401 for protected endpoints.
 */
@Component
@Slf4j
public class JwtRequestContextFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtRequestContext jwtRequestContext;

    public JwtRequestContextFilter(JwtUtil jwtUtil, JwtRequestContext jwtRequestContext) {
        this.jwtUtil = jwtUtil;
        this.jwtRequestContext = jwtRequestContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String email = jwtUtil.extractEmail(token);
            Set<Permission> permissions = jwtUtil.extractPermissions(token);
            jwtRequestContext.populate(email, permissions, token);
            log.debug("JwtRequestContext populated for user: {} with {} permissions",
                email, permissions.size());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
