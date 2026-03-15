package com.ecommerce.user.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 * Requirements: 16.1, 16.2, 16.6, 16.7
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    /**
     * Req 16.1: Successful login is logged with userId and action=LOGIN_SUCCESS.
     * Req 16.6: expiresAt = timestamp + 1 year.
     */
    @Test
    void logLoginSuccess_savesAuditLogWithCorrectFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.logLoginSuccess("42", "user@example.com", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("LOGIN_SUCCESS");
        assertThat(log.getUserId()).isEqualTo("42");
        assertThat(log.getTimestamp()).isNotNull();
        // Req 16.6: retention of 1 year
        assertThat(log.getExpiresAt()).isAfterOrEqualTo(log.getTimestamp().plusYears(1).minusSeconds(1));
    }

    /**
     * Req 16.2: Failed login is logged with null userId and email in details.
     */
    @Test
    void logLoginFailed_savesAuditLogWithNullUserIdAndEmailInDetails() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.logLoginFailed("attacker@example.com", "10.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("LOGIN_FAILED");
        assertThat(log.getUserId()).isNull();
        assertThat(log.getDetails()).contains("attacker@example.com");
    }

    /**
     * Req 16.7: User registration is logged with action=USER_REGISTERED.
     */
    @Test
    void logUserRegistered_savesAuditLogWithCorrectAction() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.logUserRegistered("99", "new@example.com", "192.168.1.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("USER_REGISTERED");
        assertThat(log.getUserId()).isEqualTo("99");
    }

    /**
     * Persist failure must be caught and not propagate (defensive logging).
     */
    @Test
    void logLoginSuccess_persistFailure_doesNotThrow() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatCode(() -> auditService.logLoginSuccess("1", "u@example.com", "1.2.3.4"))
                .doesNotThrowAnyException();
    }
}
