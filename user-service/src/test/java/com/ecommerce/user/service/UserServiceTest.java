package com.ecommerce.user.service;

import com.ecommerce.user.dto.AuthResponse;
import com.ecommerce.user.dto.LoginRequest;
import com.ecommerce.user.dto.RegisterRequest;
import com.ecommerce.user.dto.UpdateProfileRequest;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserService.
 * Validates: Requirements 1, 2
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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

    private User activeUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .role(User.Role.CUSTOMER)
                .status(User.Status.ACTIVE)
                .build();

        registerRequest = new RegisterRequest(
                "test@example.com", "Password1!", "John", "Doe", "+1234567890"
        );

        loginRequest = new LoginRequest("test@example.com", "Password1!");
    }

    // --- Registration tests ---

    @Test
    void register_success_returnsAuthResponse() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        AuthResponse response = userService.register(registerRequest);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void register_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email already exists");
    }

    // --- Login tests ---

    @Test
    void login_validCredentials_returnsAuthResponse() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Password1!", "encodedPassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        AuthResponse response = userService.login(loginRequest);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getToken()).isEqualTo("token");
        assertThat(activeUser.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_wrongPassword_throwsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_userNotFound_throwsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_inactiveUser_throwsException() {
        User suspendedUser = User.builder()
                .id(2L)
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("Jane")
                .lastName("Doe")
                .role(User.Role.CUSTOMER)
                .status(User.Status.SUSPENDED)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(suspendedUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account is not active");
    }

    // --- Audit integration tests (Req 16.1, 16.2, 16.7) ---

    @Test
    void register_callsAuditServiceLogUserRegistered() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        userService.register(registerRequest);

        verify(auditService).logUserRegistered(anyString(), anyString(), any());
    }

    @Test
    void login_success_callsAuditServiceLogLoginSuccess() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Password1!", "encodedPassword")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(jwtService.generateToken(any(User.class))).thenReturn("token");

        userService.login(loginRequest);

        verify(auditService).logLoginSuccess(anyString(), anyString(), any());
    }

    @Test
    void login_wrongPassword_callsAuditServiceLogLoginFailed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(RuntimeException.class);

        verify(auditService).logLoginFailed(anyString(), any());
    }

    // --- Profile update tests ---

    @Test
    void updateProfile_differentUser_throwsAccessDenied() {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane", "Smith", "+9876543210");

        assertThatThrownBy(() -> userService.updateProfile(1L, 2L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access denied");
    }
}
