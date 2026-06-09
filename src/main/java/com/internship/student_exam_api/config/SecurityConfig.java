package com.internship.student_exam_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.internship.student_exam_api.dto.response.ApiErrorResponse;
import com.internship.student_exam_api.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;

/**
 * Spring Security 6 configuration — SecurityFilterChain approach.
 *
 * <p>KEY DESIGN DECISIONS:</p>
 * <ol>
 *   <li><strong>STATELESS session</strong>: No HttpSessions. Each request is
 *       authenticated independently via JWT.</li>
 *   <li><strong>CSRF disabled</strong>: Stateless JWT API with no cookies,
 *       so CSRF attacks do not apply.</li>
 *   <li><strong>JwtAuthFilter BEFORE UsernamePasswordAuthenticationFilter</strong>:
 *       Our filter runs first to handle JWT auth from Authorization header.</li>
 *   <li><strong>Custom AuthenticationEntryPoint</strong>: Returns 401 JSON
 *       for unauthenticated requests (Spring defaults to 403 without this).</li>
 *   <li><strong>Custom AccessDeniedHandler</strong>: Returns 403 JSON with
 *       a proper error body for insufficient role (Spring defaults to empty body).</li>
 * </ol>
 *
 * <p>WHY handlers are configured here instead of GlobalExceptionHandler:</p>
 * <p>Spring Security processes AccessDeniedException and AuthenticationException
 * in its own filter chain BEFORE the request reaches DispatcherServlet.
 * {@code @ExceptionHandler} in {@code @ControllerAdvice} only catches exceptions
 * thrown inside controller methods. Security exceptions are thrown outside that scope.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final com.internship.student_exam_api.security.context.JwtRequestContextFilter jwtRequestContextFilter;

    /**
     * Constructs SecurityConfig using constructor injection.
     *
     * @param jwtAuthFilter      JWT token authentication filter
     * @param userDetailsService Spring Security user details service
     * @param jwtRequestContextFilter parses permissions into JwtRequestContext
     */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsService userDetailsService, com.internship.student_exam_api.security.context.JwtRequestContextFilter jwtRequestContextFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.jwtRequestContextFilter = jwtRequestContextFilter;
    }

    /**
     * Defines the security filter chain with stateless JWT authentication,
     * RBAC endpoint rules, and custom error handlers for 401/403 responses.
     *
     * @param http security builder
     * @return SecurityFilterChain instance
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security Filter Chain");
        return http
            // Disable CSRF — stateless JWT API, no cookies
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless sessions — no HttpSession, no JSESSIONID
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Custom error handlers for security exceptions
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: login endpoint
                .requestMatchers("/api/auth/**").permitAll()

                // Public: Swagger UI
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Anything else requires authentication
                .anyRequest().authenticated()
            )

            // Insert JWT filter before Spring's default login filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtRequestContextFilter, JwtAuthFilter.class)

            .build();
    }

    /**
     * Custom AuthenticationEntryPoint — returns HTTP 401 with JSON body
     * when an unauthenticated user tries to access a protected endpoint.
     *
     * <p>Without this, Spring Security defaults to returning 403 Forbidden
     * with no body, which is incorrect per HTTP spec (401 should be returned
     * when authentication is required but not provided).</p>
     *
     * @return AuthenticationEntryPoint that writes JSON 401 response
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthenticated request to {}: {}", request.getRequestURI(), authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(401)
                .error("UNAUTHORIZED")
                .message("Authentication required. Please provide a valid JWT token.")
                .timestamp(LocalDateTime.now())
                .build();

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            response.getWriter().write(mapper.writeValueAsString(errorResponse));
        };
    }

    /**
     * Custom AccessDeniedHandler — returns HTTP 403 with JSON body
     * when an authenticated user lacks the required role for an endpoint.
     *
     * <p>Without this, Spring Security returns 403 with an empty body,
     * so the client cannot parse a structured error response.</p>
     *
     * @return AccessDeniedHandler that writes JSON 403 response
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access denied for {} to {}: {}", request.getRemoteUser(),
                request.getRequestURI(), accessDeniedException.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .status(403)
                .error("FORBIDDEN")
                .message("You do not have permission to perform this action")
                .timestamp(LocalDateTime.now())
                .build();

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            response.getWriter().write(mapper.writeValueAsString(errorResponse));
        };
    }

    /**
     * BCryptPasswordEncoder — the standard for production password hashing.
     * Default cost factor of 10 (~100ms per hash).
     *
     * @return PasswordEncoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the DaoAuthenticationProvider bean which wires
     * UserDetailsService + PasswordEncoder together. Spring's
     * AuthenticationManager delegates to this provider during login.
     *
     * @return DaoAuthenticationProvider instance
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes AuthenticationManager bean for AuthController to inject.
     *
     * @param config authentication configuration
     * @return AuthenticationManager instance
     * @throws Exception if manager exposure fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
