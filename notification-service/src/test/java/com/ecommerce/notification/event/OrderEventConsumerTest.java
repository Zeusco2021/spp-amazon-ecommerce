package com.ecommerce.notification.event;

import com.ecommerce.notification.client.UserServiceClient;
import com.ecommerce.notification.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderEventConsumer.
 * Validates that Kafka events trigger the correct email notifications.
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.5
 */
@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderEventConsumer consumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Inject the real ObjectMapper into the consumer
        consumer = new OrderEventConsumer(emailService, userServiceClient, objectMapper);
    }

    // -------------------------------------------------------------------------
    // onOrderConfirmed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onOrderConfirmed sends confirmation email when user email is resolved - Req 12.1")
    void onOrderConfirmed_sendsConfirmationEmail() {
        when(userServiceClient.getUserEmail(1L)).thenReturn("customer@example.com");

        Map<String, Object> payload = buildOrderPayload(1L, "ORD-20240101-000001", 1L, new BigDecimal("99.99"));

        consumer.onOrderConfirmed(payload);

        verify(emailService).sendOrderConfirmation("customer@example.com", "ORD-20240101-000001", new BigDecimal("99.99"));
    }

    @Test
    @DisplayName("onOrderConfirmed skips email when user email cannot be resolved - Req 12.1")
    void onOrderConfirmed_skipsWhenEmailNotResolved() {
        when(userServiceClient.getUserEmail(1L)).thenReturn(null);

        Map<String, Object> payload = buildOrderPayload(1L, "ORD-20240101-000001", 1L, new BigDecimal("99.99"));

        consumer.onOrderConfirmed(payload);

        verify(emailService, never()).sendOrderConfirmation(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // onOrderShipped
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onOrderShipped sends shipping notification with tracking number - Req 12.2")
    void onOrderShipped_sendsShippingNotification() {
        when(userServiceClient.getUserEmail(2L)).thenReturn("customer@example.com");

        Map<String, Object> payload = buildShippedPayload(2L, "ORD-20240101-000002", 2L,
                new BigDecimal("49.99"), "TRK-ABC12345");

        consumer.onOrderShipped(payload);

        verify(emailService).sendShippingNotification("customer@example.com", "ORD-20240101-000002", "TRK-ABC12345");
    }

    @Test
    @DisplayName("onOrderShipped skips email when user email cannot be resolved - Req 12.2")
    void onOrderShipped_skipsWhenEmailNotResolved() {
        when(userServiceClient.getUserEmail(2L)).thenReturn(null);

        Map<String, Object> payload = buildShippedPayload(2L, "ORD-20240101-000002", 2L,
                new BigDecimal("49.99"), "TRK-ABC12345");

        consumer.onOrderShipped(payload);

        verify(emailService, never()).sendShippingNotification(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // onOrderDelivered
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onOrderDelivered sends delivery confirmation email - Req 12.3")
    void onOrderDelivered_sendsDeliveryConfirmation() {
        when(userServiceClient.getUserEmail(3L)).thenReturn("customer@example.com");

        Map<String, Object> payload = buildOrderPayload(3L, "ORD-20240101-000003", 3L, new BigDecimal("29.99"));

        consumer.onOrderDelivered(payload);

        verify(emailService).sendDeliveryConfirmation("customer@example.com", "ORD-20240101-000003");
    }

    @Test
    @DisplayName("onOrderDelivered skips email when user email cannot be resolved - Req 12.3")
    void onOrderDelivered_skipsWhenEmailNotResolved() {
        when(userServiceClient.getUserEmail(3L)).thenReturn(null);

        Map<String, Object> payload = buildOrderPayload(3L, "ORD-20240101-000003", 3L, new BigDecimal("29.99"));

        consumer.onOrderDelivered(payload);

        verify(emailService, never()).sendDeliveryConfirmation(any(), any());
    }

    // -------------------------------------------------------------------------
    // onOrderCancelled
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onOrderCancelled sends cancellation email with reason - Req 12.4")
    void onOrderCancelled_sendsCancellationEmail() {
        when(userServiceClient.getUserEmail(4L)).thenReturn("customer@example.com");

        Map<String, Object> payload = buildOrderPayload(4L, "ORD-20240101-000004", 4L, new BigDecimal("79.99"));
        payload.put("reason", "Payment failed");

        consumer.onOrderCancelled(payload);

        verify(emailService).sendCancellationNotification("customer@example.com", "ORD-20240101-000004", "Payment failed");
    }

    @Test
    @DisplayName("onOrderCancelled sends cancellation email with null reason when not provided - Req 12.4")
    void onOrderCancelled_sendsEmailWithNullReasonWhenAbsent() {
        when(userServiceClient.getUserEmail(4L)).thenReturn("customer@example.com");

        Map<String, Object> payload = buildOrderPayload(4L, "ORD-20240101-000004", 4L, new BigDecimal("79.99"));
        // no "reason" key in payload

        consumer.onOrderCancelled(payload);

        verify(emailService).sendCancellationNotification("customer@example.com", "ORD-20240101-000004", null);
    }

    @Test
    @DisplayName("onOrderCancelled skips email when user email cannot be resolved - Req 12.4")
    void onOrderCancelled_skipsWhenEmailNotResolved() {
        when(userServiceClient.getUserEmail(4L)).thenReturn(null);

        Map<String, Object> payload = buildOrderPayload(4L, "ORD-20240101-000004", 4L, new BigDecimal("79.99"));

        consumer.onOrderCancelled(payload);

        verify(emailService, never()).sendCancellationNotification(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildOrderPayload(Long orderId, String orderNumber, Long userId, BigDecimal total) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNumber", orderNumber);
        payload.put("userId", userId);
        payload.put("items", List.of());
        payload.put("total", total);
        payload.put("createdAt", LocalDateTime.now().toString());
        return payload;
    }

    private Map<String, Object> buildShippedPayload(Long orderId, String orderNumber, Long userId,
                                                     BigDecimal total, String trackingNumber) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNumber", orderNumber);
        payload.put("userId", userId);
        payload.put("total", total);
        payload.put("trackingNumber", trackingNumber);
        payload.put("shippedAt", LocalDateTime.now().toString());
        return payload;
    }
}
