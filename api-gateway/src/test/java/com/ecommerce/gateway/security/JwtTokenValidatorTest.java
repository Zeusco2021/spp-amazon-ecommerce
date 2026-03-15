package com.ecommerce.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JWT token validation.
 * Validates Requirements 13.1, 13.2, 13.3.
 */
class JwtTokenValidatorTest {

    private static final String SECRET = "myTestSecretKeyForJWTValidationTesting123456789";
    private JwtTokenValidator validator;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        validator = new JwtTokenValidator(SECRET);
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String buildToken(Long userId, String email, String role, long expirationMs) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("Valid token should pass validation")
    void validToken_isValid() {
        String token = buildToken(1L, "user@example.com", "CUSTOMER", 3600_000);
        assertThat(validator.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("Expired token should fail validation - Requirement 13.3")
    void expiredToken_isInvalid() {
        String token = buildToken(1L, "user@example.com", "CUSTOMER", -1000);
        assertThat(validator.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("Tampered token should fail validation - Requirement 13.3")
    void tamperedToken_isInvalid() {
        String token = buildToken(1L, "user@example.com", "CUSTOMER", 3600_000);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(validator.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("Blank token should fail validation")
    void blankToken_isInvalid() {
        assertThat(validator.isValid("")).isFalse();
        assertThat(validator.isValid("   ")).isFalse();
    }

    @Test
    @DisplayName("getUserId extracts userId claim - Requirement 13.2")
    void getUserId_extractsCorrectly() {
        String token = buildToken(42L, "user@example.com", "CUSTOMER", 3600_000);
        assertThat(validator.getUserId(token)).isEqualTo("42");
    }

    @Test
    @DisplayName("getEmail extracts email claim")
    void getEmail_extractsCorrectly() {
        String token = buildToken(1L, "test@example.com", "CUSTOMER", 3600_000);
        assertThat(validator.getEmail(token)).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("getRole extracts role claim")
    void getRole_extractsCorrectly() {
        String token = buildToken(1L, "admin@example.com", "ADMIN", 3600_000);
        assertThat(validator.getRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Token signed with different secret should fail - Requirement 13.3")
    void tokenWithWrongSecret_isInvalid() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "differentSecretKeyThatIsLongEnoughForHMAC256".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", 1L)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(otherKey)
                .compact();

        assertThat(validator.isValid(token)).isFalse();
    }
}
