package com.internship.student_exam_api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.internship.student_exam_api.enums.Role;
import com.internship.student_exam_api.security.permission.Permission;
import com.internship.student_exam_api.security.permission.RolePermissions;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to generate, parse, and validate JSON Web Tokens (JWT).
 * Utilizes HMAC-SHA algorithms using key sizes of 256 bits or greater.
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Generates a stateless JWT token using user details.
     *
     * @param userDetails wrapped user details
     * @return signed compact JWT string
     */
    public String generateToken(AppUserDetails userDetails) {
        Set<Permission> permissions = RolePermissions.forRole(Role.valueOf(userDetails.getRoleName()));

        // Convert Permission enum values to strings for JWT serialization
        List<String> permissionNames = permissions.stream()
            .map(Permission::name)
            .collect(Collectors.toList());

        return Jwts.builder()
            .subject(userDetails.getUsername())
            .claim("permissions", permissionNames)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Validates the JWT token signature and expiration status.
     *
     * @param token candidate JWT token
     * @return true if signature is valid and token is unexpired, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the user email (subject claim) from the token.
     *
     * @param token JWT token
     * @return extracted email string
     */
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extracts the user role name from the token's claims.
     *
     * @param token JWT token
     * @return extracted role string, or {@code null} for tokens generated after
     *         the Phase 1 permission migration (which no longer embed a "role" claim)
     * @deprecated Since Phase 1 AOP Authorization migration. The "role" claim has been
     *             replaced by a "permissions" array claim. Use {@link #extractPermissions(String)}
     *             instead. This method will be removed in Phase 3.
     */
    @Deprecated(since = "Phase 1", forRemoval = true)
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    /**
     * Extracts the user permissions from the token's claims.
     *
     * @param token JWT token
     * @return extracted Set of Permissions
     */
    @SuppressWarnings("unchecked")
    public Set<Permission> extractPermissions(String token) {
        List<String> permNames = (List<String>) extractClaims(token)
            .get("permissions", List.class);

        if (permNames == null) return EnumSet.noneOf(Permission.class);

        return permNames.stream()
            .map(name -> {
                try { return Permission.valueOf(name); }
                catch (IllegalArgumentException e) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
