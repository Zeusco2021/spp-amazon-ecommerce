package com.ecommerce.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validates JWT tokens and extracts claims.
 * Satisfies Requirements 13.1, 13.2, 13.3.
 */
@Slf4j
@Component
public class JwtTokenValidator {

    private final SecretKey secretKey;

    public JwtTokenValidator(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates the token signature and expiration.
     *
     * @param token raw JWT (without "Bearer " prefix)
     * @return true if valid
     */
    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts all claims from a valid token.
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the userId claim from a valid token.
     */
    public String getUserId(String token) {
        Claims claims = getClaims(token);
        Object userId = claims.get("userId");
        return userId != null ? userId.toString() : claims.getSubject();
    }

    /**
     * Extracts the email claim from a valid token.
     */
    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * Extracts the role claim from a valid token.
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }
}
