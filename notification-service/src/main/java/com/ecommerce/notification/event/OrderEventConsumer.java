package com.ecommerce.notification.event;

import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderShippedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.notification.client.UserServiceClient;
import com.ecommerce.notification.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer for order lifecycle events.
 *
 * Consumes:
 * - order.confirmed  → sends order confirmation email (Req 12.1)
 * - order.shipped    → sends shipping notification with tracking number (Req 12.2)
 * - order.delivered  → sends delivery confirmation email (Req 12.3)
 * - order.cancelled  → sends cancellation email with reason (Req 12.4)
 *
 * Requirements: 12.1, 12.2, 12.3, 12.4, 12.5
 */
@Component
public class OrderEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final EmailService emailService;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(EmailService emailService,
                              UserServiceClient userServiceClient,
                              ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.userServiceClient = userServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes OrderConfirmedEvent and sends a confirmation email to the customer.
     * Requirements: 12.1, 12.5
     */
    @KafkaListener(topics = KafkaTopics.ORDER_CONFIRMED, groupId = "notification-service")
    public void onOrderConfirmed(Map<String, Object> payload) {
        try {
            OrderCreatedEvent event = objectMapper.convertValue(payload, OrderCreatedEvent.class);
            logger.info("Received order.confirmed event for orderId={}, orderNumber={}",
                    event.orderId(), event.orderNumber());

            String email = resolveUserEmail(event.userId(), event.orderId());
            if (email == null) return;

            emailService.sendOrderConfirmation(email, event.orderNumber(), event.total());
        } catch (Exception e) {
            logger.error("Error processing order.confirmed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consumes OrderShippedEvent and sends a shipping notification with tracking number.
     * Requirements: 12.2, 12.5
     */
    @KafkaListener(topics = KafkaTopics.ORDER_SHIPPED, groupId = "notification-service")
    public void onOrderShipped(Map<String, Object> payload) {
        try {
            OrderShippedEvent event = objectMapper.convertValue(payload, OrderShippedEvent.class);
            logger.info("Received order.shipped event for orderId={}, orderNumber={}",
                    event.orderId(), event.orderNumber());

            String email = resolveUserEmail(event.userId(), event.orderId());
            if (email == null) return;

            emailService.sendShippingNotification(email, event.orderNumber(), event.trackingNumber());
        } catch (Exception e) {
            logger.error("Error processing order.shipped event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consumes OrderDeliveredEvent and sends a delivery confirmation email.
     * Requirements: 12.3, 12.5
     */
    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-service")
    public void onOrderDelivered(Map<String, Object> payload) {
        try {
            OrderCreatedEvent event = objectMapper.convertValue(payload, OrderCreatedEvent.class);
            logger.info("Received order.delivered event for orderId={}, orderNumber={}",
                    event.orderId(), event.orderNumber());

            String email = resolveUserEmail(event.userId(), event.orderId());
            if (email == null) return;

            emailService.sendDeliveryConfirmation(email, event.orderNumber());
        } catch (Exception e) {
            logger.error("Error processing order.delivered event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consumes OrderCancelledEvent and sends a cancellation email with reason.
     * Requirements: 12.4, 12.5
     */
    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service")
    public void onOrderCancelled(Map<String, Object> payload) {
        try {
            OrderCreatedEvent event = objectMapper.convertValue(payload, OrderCreatedEvent.class);
            logger.info("Received order.cancelled event for orderId={}, orderNumber={}",
                    event.orderId(), event.orderNumber());

            String email = resolveUserEmail(event.userId(), event.orderId());
            if (email == null) return;

            String reason = extractStringField(payload, "reason");
            emailService.sendCancellationNotification(email, event.orderNumber(), reason);
        } catch (Exception e) {
            logger.error("Error processing order.cancelled event: {}", e.getMessage(), e);
        }
    }

    /**
     * Resolves the user's email address via the User Service.
     * Logs a warning and returns null if the email cannot be resolved.
     */
    private String resolveUserEmail(Long userId, Long orderId) {
        String email = userServiceClient.getUserEmail(userId);
        if (email == null) {
            logger.warn("Could not resolve email for userId={}, orderId={} — skipping notification",
                    userId, orderId);
        }
        return email;
    }

    /**
     * Safely extracts a String field from the raw payload map.
     */
    private String extractStringField(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        return value != null ? value.toString() : null;
    }
}
