package com.ecommerce.user.service;

import com.ecommerce.user.dto.AuthResponse;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Email Uniqueness
 *
 * Propiedad 4: Unicidad de Emails
 * Validates: Requirements 1.2, 15.4
 *
 * Formal property:
 * ∀ usuario U1, U2:
 *   (U1 ≠ U2 ∧ estado(U1) = ACTIVE ∧ estado(U2) = ACTIVE) ⟹ email(U1) ≠ email(U2)
 */
@ExtendWith(MockitoExtension.class)
class EmailUniquenessPropertyTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.ecommerce.user.audit.AuditService auditService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        // Default: encode returns a fixed hash
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedpassword");
        lenient().when(jwtService.generateToken(any(User.class))).thenReturn("mock.jwt.token");
    }

    // -------------------------------------------------------------------------
    // Propiedad 4: Unicidad de Emails — @RepeatedTest variant (JUnit 5)
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.2, 15.4**
     *
     * Property: For any valid email, registering the same email twice ALWAYS
     * throws a RuntimeException with message "Email already exists".
     * Repeated 10 times with random emails to verify the property holds
     * across multiple inputs.
     */
    @RepeatedTest(10)
    void emailUniqueness_registerSameEmailTwice_alwaysThrows() {
        // Generate a random email for each repetition
        String email = "user-" + UUID.randomUUID() + "@example.com";

        RegisterRequest firstRequest = buildRequest(email);
        RegisterRequest secondRequest = buildRequest(email);

        User savedUser = buildUser(1L, email);

        // First registration: email does not exist yet
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // First call should succeed
        AuthResponse firstResponse = userService.register(firstRequest);
        assertNotNull(firstResponse);
        assertEquals(email, firstResponse.getEmail());

        // Second registration: email now exists
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // Second call must throw
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.register(secondRequest));
        assertEquals("Email already exists", ex.getMessage());
    }

    /**
     * **Validates: Requirements 1.2, 15.4**
     *
     * Property: Two different emails can both register successfully.
     */
    @Test
    void emailUniqueness_differentEmails_bothSucceed() {
        String email1 = "alice-" + UUID.randomUUID() + "@example.com";
        String email2 = "bob-" + UUID.randomUUID() + "@example.com";

        User user1 = buildUser(1L, email1);
        User user2 = buildUser(2L, email2);

        // Both emails are new
        when(userRepository.existsByEmail(email1)).thenReturn(false);
        when(userRepository.existsByEmail(email2)).thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(user1)
                .thenReturn(user2);

        AuthResponse response1 = userService.register(buildRequest(email1));
        AuthResponse response2 = userService.register(buildRequest(email2));

        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals(email1, response1.getEmail());
        assertEquals(email2, response2.getEmail());
    }

    /**
     * **Validates: Requirements 1.2, 15.4**
     *
     * Property: Email comparison must be consistent.
     *
     * Note: MySQL handles case-insensitivity at the DB level (utf8mb4_unicode_ci
     * collation). The repository's existsByEmail delegates to MySQL, so the DB
     * enforces that "User@Example.com" and "user@example.com" are the same email.
     * This test documents that behaviour: when the repository reports the email
     * already exists (as MySQL would for a case-insensitive match), the service
     * correctly rejects the duplicate.
     */
    @Test
    void emailUniqueness_caseInsensitive_sameEmailDifferentCase() {
        String lowerEmail = "testuser@example.com";
        String upperEmail = "TESTUSER@EXAMPLE.COM";

        User savedUser = buildUser(1L, lowerEmail);

        // First registration with lowercase email
        when(userRepository.existsByEmail(lowerEmail)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse firstResponse = userService.register(buildRequest(lowerEmail));
        assertNotNull(firstResponse);

        // MySQL (case-insensitive collation) would return true for the uppercase variant
        when(userRepository.existsByEmail(upperEmail)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.register(buildRequest(upperEmail)));
        assertEquals("Email already exists", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // jqwik @Property variant — same property with generated email strings
    // -------------------------------------------------------------------------

    /**
     * **Validates: Requirements 1.2, 15.4**
     *
     * jqwik property: for any generated email string, registering it twice
     * always throws "Email already exists".
     *
     * Note: jqwik does not process @ExtendWith(MockitoExtension.class), so
     * mocks are created manually per try to ensure isolation.
     */
    @Property(tries = 20)
    void emailUniqueness_jqwik_registerSameEmailTwice_alwaysThrows(
            @ForAll("validEmails") String email) {

        // Create fresh mocks for each jqwik try (jqwik ignores @Mock fields)
        UserRepository mockRepo = mock(UserRepository.class);
        JwtService mockJwt = mock(JwtService.class);
        PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
        UserService svc = new UserService(mockRepo, mockJwt, mockEncoder,
                mock(com.ecommerce.user.audit.AuditService.class));

        when(mockEncoder.encode(anyString())).thenReturn("$2a$12$hashedpassword");
        when(mockJwt.generateToken(any(User.class))).thenReturn("mock.jwt.token");

        User savedUser = buildUser(1L, email);

        // First registration succeeds
        when(mockRepo.existsByEmail(email)).thenReturn(false);
        when(mockRepo.save(any(User.class))).thenReturn(savedUser);

        AuthResponse firstResponse = svc.register(buildRequest(email));
        assertNotNull(firstResponse);

        // Second registration must fail
        when(mockRepo.existsByEmail(email)).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> svc.register(buildRequest(email)));
        assertEquals("Email already exists", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // Arbitraries / generators
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> validEmails() {
        // Generate realistic-looking email addresses
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

    private RegisterRequest buildRequest(String email) {
        return new RegisterRequest(email, "Password1!", "John", "Doe", "+1234567890");
    }

    private User buildUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .password("$2a$12$hashedpassword")
                .firstName("John")
                .lastName("Doe")
                .role(User.Role.CUSTOMER)
                .status(User.Status.ACTIVE)
                .build();
    }
}
