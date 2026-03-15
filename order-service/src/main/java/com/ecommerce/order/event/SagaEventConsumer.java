package com.ecommerce.order.event;

import com.ecommerce.common.event.InventoryReservedEvent;
import com.ecommerce.common.event.InventoryUnavailableEvent;
import com.ecommerce.common.event.PaymentFailedEvent;
import com.ecommerce.common.event.PaymentSuccessEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consumes Saga-related events and orchestrates order state transitions.
 *
 * Saga flow:
 *   OrderCreatedEvent → [Inventory Service] → InventoryReservedEvent / InventoryUnavailableEvent
 *   InventoryReservedEvent → [Payment Service] → PaymentSuccessEvent / PaymentFailedEvent
 *   PaymentSuccessEvent → Order CONFIRMED + publish ORDER_CONFIRMED
 *   PaymentFailedEvent / InventoryUnavailableEvent → Order CANCELLED + publish ORDER_CANCELLED
 *
 * Requirements: 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11, 7.12, 7.13
 */
@Component
public class SagaEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SagaEventConsumer.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public SagaEventConsumer(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * Inventory reserved → move order to PROCESSING.
     * Payment service will pick up the InventoryReservedEvent and process payment.
     * Requirements: 7.5, 7.6
     */
    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "order-service")
    @Transactional
    public void onInventoryReserved(Map<String, Object> payload) {
        try {
            InventoryReservedEvent event = objectMapper.convertValue(payload, InventoryReservedEvent.class);
            logger.info("Received InventoryReservedEvent for orderId={}", event.orderId());
            orderService.onInventoryReserved(event.orderId());
        } catch (Exception e) {
            logger.error("Error processing InventoryReservedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Inventory unavailable → cancel order and publish ORDER_CANCELLED for inventory release.
     * Requirements: 7.7, 7.12
     */
    @KafkaListener(topics = KafkaTopics.INVENTORY_UNAVAILABLE, groupId = "order-service")
    @Transactional
    public void onInventoryUnavailable(Map<String, Object> payload) {
        try {
            InventoryUnavailableEvent event = objectMapper.convertValue(payload, InventoryUnavailableEvent.class);
            logger.info("Received InventoryUnavailableEvent for orderId={}: {}", event.orderId(), event.reason());
            orderService.onInventoryUnavailable(event.orderId(), event.orderNumber());
        } catch (Exception e) {
            logger.error("Error processing InventoryUnavailableEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Payment success → confirm order and publish ORDER_CONFIRMED.
     * Requirements: 7.9, 7.11
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "order-service")
    @Transactional
    public void onPaymentSuccess(Map<String, Object> payload) {
        try {
            PaymentSuccessEvent event = objectMapper.convertValue(payload, PaymentSuccessEvent.class);
            logger.info("Received PaymentSuccessEvent for orderId={}, paymentId={}", event.orderId(), event.paymentId());
            orderService.onPaymentSuccess(event.orderId(), event.paymentId());
        } catch (Exception e) {
            logger.error("Error processing PaymentSuccessEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Payment failed → cancel order and publish ORDER_CANCELLED for inventory release.
     * Requirements: 7.10, 7.12, 7.13
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service")
    @Transactional
    public void onPaymentFailed(Map<String, Object> payload) {
        try {
            PaymentFailedEvent event = objectMapper.convertValue(payload, PaymentFailedEvent.class);
            logger.info("Received PaymentFailedEvent for orderId={}: {}", event.orderId(), event.reason());
            orderService.onPaymentFailed(event.orderId(), event.orderNumber());
        } catch (Exception e) {
            logger.error("Error processing PaymentFailedEvent: {}", e.getMessage(), e);
        }
    }
}
