package com.ecommerce.user.service;

import com.ecommerce.user.entity.User;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-Based Tests for JWT Token Validity
 *
 * Propiedad 5: Validez de Tokens JWT
 * Validates: Requirements 1.5, 1.6
 *
 * Formal property:
 * ∀ usuario U: validateToken(generateToken(U)) = true
 * ∀ token T: extractUserId(T) = userId del usuario que generó T
 * ∀ token T modificado T': validateToken(T') = false
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenPropertyTest {

    private static final String TEST_SECRET = "testSecretKeyForTestingPurposesOnly12345678";
    private static final long TEST_EXPIRATION = 86400000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", TEST_EXPIRATION);
    }

    // -------------------------------------------------------------------------
    // a) @RepeatedTest(10) — token generated is always valid
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.5, 1.6**
     *
     * Property: For any user (random id, email, role), generateToken() produces
     * a token that validateToken() returns true.
     * Repeated 10 times with random inputs to verify the property holds broadly.
     */
    @RepeatedTest(10)
    void tokenValidity_generatedTokenIsAlwaysValid() {
        long randomId = (long) (Math.random() * Long.MAX_VALUE) + 1;
        String randomEmail = "user-" + UUID.randomUUID() + "@example.com";
        User.Role randomRole = User.Role.values()[(int) (Math.random() * User.Role.values().length)];

        User user = buildUser(randomId, randomEmail, randomRole);
        String token = jwtService.generateToken(user);

        assertNotNull(token, "Generated token must not be null");
        assertFalse(token.isBlank(), "Generated token must not be blank");
        assertTrue(jwtService.validateToken(token),
                "Token generated for user " + randomId + " must be valid");
    }

    // -------------------------------------------------------------------------
    // b) @Test — extracted userId matches original
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.5, 1.6**
     *
     * Property: generateToken(user) then extractUserId(token) returns the same userId.
     */
    @Test
    void tokenValidity_extractedUserIdMatchesOriginal() {
        User user = buildUser(42L, "alice@example.com", User.Role.CUSTOMER);

        String token = jwtService.generateToken(user);
        Long extractedId = jwtService.extractUserId(token);

        assertEquals(user.getId(), extractedId,
                "Extracted userId must match the original userId used to generate the token");
    }

    // -------------------------------------------------------------------------
    // c) @Test — tampered token is invalid
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.5, 1.6**
     *
     * Property: A token with any modification to its content must be rejected.
     */
    @Test
    void tokenValidity_tamperedTokenIsInvalid() {
        User user = buildUser(7L, "bob@example.com", User.Role.SELLER);
        String token = jwtService.generateToken(user);

        // Tamper the payload section (middle part of JWT)
        String[] parts = token.split("\\.");
        assertTrue(parts.length == 3, "JWT must have 3 parts");

        // Flip the last character of the payload to corrupt the signature
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 1)
                + (parts[2].endsWith("a") ? "b" : "a");
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertFalse(jwtService.validateToken(tamperedToken),
                "A tampered token must be rejected by validateToken");
    }

    // -------------------------------------------------------------------------
    // d) @Test — empty/null token is invalid
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.5, 1.6**
     *
     * Property: Empty and null tokens must always be invalid.
     */
    @Test
    void tokenValidity_emptyTokenIsInvalid() {
        assertFalse(jwtService.validateToken(""),
                "Empty string must not be a valid token");

        // Null should return false without throwing NPE
        boolean nullResult;
        try {
            nullResult = jwtService.validateToken(null);
        } catch (NullPointerException e) {
            // If NPE is thrown, the service doesn't handle null gracefully — fail explicitly
            fail("validateToken(null) must not throw NullPointerException; it should return false");
            return;
        }
        assertFalse(nullResult, "null must not be a valid token");
    }

    // -------------------------------------------------------------------------
    // e) @Property(tries=20) with jqwik — any user token is valid
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.5, 1.6**
     *
     * jqwik property: For any generated userId and email, the token is valid
     * and the extracted userId matches the original.
     *
     * Note: jqwik does not process @ExtendWith(MockitoExtension.class), so
     * JwtService is instantiated manually per try to ensure isolation.
     */
    @Property(tries = 20)
    void tokenValidity_jqwik_anyUserTokenIsValid(
            @ForAll @LongRange(min = 1) Long userId,
            @ForAll("validEmails") String email) {

        // Create a fresh JwtService for each jqwik try (jqwik ignores @BeforeEach)
        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(svc, "expiration", TEST_EXPIRATION);

        User user = buildUser(userId, email, User.Role.CUSTOMER);

        String token = svc.generateToken(user);

        assertTrue(svc.validateToken(token),
                "Token for userId=" + userId + " must be valid");
        assertEquals(userId, svc.extractUserId(token),
                "Extracted userId must match original for userId=" + userId);
    }

    // -------------------------------------------------------------------------
    // Arbitraries / generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> localPart = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(10);

        Arbitrary<String> domain = Arbitraries.of(
                "example.com", "test.org", "mail.net", "demo.io", "sample.co");

        return Combinators.combine(localPart, domain)
                .as((local, dom) -> local + "@" + dom);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildUser(Long id, String email, User.Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .password("$2a$12$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
    }
}
