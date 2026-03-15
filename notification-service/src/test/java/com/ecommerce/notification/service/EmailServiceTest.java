package com.ecommerce.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for EmailService.
 * Validates that emails are sent with correct recipients, subjects and content.
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.6
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@ecommerce.com");
    }

    @Test
    @DisplayName("sendOrderConfirmation sends email with correct subject and recipient - Req 12.1")
    void sendOrderConfirmation_sendsCorrectEmail() {
        emailService.sendOrderConfirmation("user@example.com", "ORD-20240101-000001", new BigDecimal("99.99"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).contains("ORD-20240101-000001");
        assertThat(msg.getText()).contains("ORD-20240101-000001");
        assertThat(msg.getText()).contains("99.99");
    }

    @Test
    @DisplayName("sendShippingNotification sends email with tracking number - Req 12.2")
    void sendShippingNotification_includesTrackingNumber() {
        emailService.sendShippingNotification("user@example.com", "ORD-20240101-000002", "TRK-ABC12345");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).contains("ORD-20240101-000002");
        assertThat(msg.getText()).contains("TRK-ABC12345");
    }

    @Test
    @DisplayName("sendDeliveryConfirmation sends email with order number - Req 12.3")
    void sendDeliveryConfirmation_sendsCorrectEmail() {
        emailService.sendDeliveryConfirmation("user@example.com", "ORD-20240101-000003");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).contains("ORD-20240101-000003");
        assertThat(msg.getText()).contains("ORD-20240101-000003");
    }

    @Test
    @DisplayName("sendCancellationNotification sends email with cancellation reason - Req 12.4")
    void sendCancellationNotification_includesReason() {
        emailService.sendCancellationNotification("user@example.com", "ORD-20240101-000004", "Payment failed");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).contains("ORD-20240101-000004");
        assertThat(msg.getText()).contains("Payment failed");
    }

    @Test
    @DisplayName("sendCancellationNotification handles null reason gracefully - Req 12.4")
    void sendCancellationNotification_nullReason_sendsEmail() {
        emailService.sendCancellationNotification("user@example.com", "ORD-20240101-000004", null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getText()).contains("Cancelled by request");
    }
}
