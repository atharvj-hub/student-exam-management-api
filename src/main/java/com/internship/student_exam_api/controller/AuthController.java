package com.internship.student_exam_api.controller;

import com.internship.student_exam_api.dto.request.LoginRequest;
import com.internship.student_exam_api.dto.response.LoginResponse;
import com.internship.student_exam_api.security.AppUserDetails;
import com.internship.student_exam_api.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import com.internship.student_exam_api.service.RateLimitService;

/**
 * Controller for handling authentication requests like user login.
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RateLimitService rateLimitService;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Constructs AuthController using constructor injection.
     *
     * @param authenticationManager the Spring Security authentication manager
     * @param jwtUtil token utility class
     * @param rateLimitService rate limit service
     */
    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, RateLimitService rateLimitService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.rateLimitService = rateLimitService;
    }

    /**
     * Endpoint to authenticate users and return a JWT access token.
     *
     * @param request LoginRequest holding email and password
     * @return ResponseEntity holding LoginResponse details
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = httpRequest.getRemoteAddr();
        }

        if (!rateLimitService.resolveBucket(ip).tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        log.info("Processing login request for user: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        log.info("User {} successfully authenticated with role {}", userDetails.getUsername(), userDetails.getRoleName());

        return ResponseEntity.ok(
            LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role(userDetails.getRoleName())
                .email(userDetails.getUsername())
                .expiresIn(expirationMs)
                .build()
        );
    }
}
