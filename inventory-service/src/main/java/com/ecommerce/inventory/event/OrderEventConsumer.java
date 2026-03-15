package com.ecommerce.inventory.event;

import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderItemEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Consumes order lifecycle events and manages inventory accordingly.
 *
 * - OrderCreatedEvent  → reserve inventory, publish InventoryReservedEvent or InventoryUnavailableEvent
 * - OrderCancelledEvent → release reserved inventory
 * - PaymentFailedEvent  → release reserved inventory (saga compensation)
 *
 * Requirements: 7.5, 7.6, 7.7, 7.13
 */
@Component
public class OrderEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume OrderCreatedEvent and attempt to reserve inventory.
     * Publishes InventoryReservedEvent on success, InventoryUnavailableEvent on failure.
     * Requirements: 7.5, 7.6, 7.7
     */
    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "inventory-service")
    public void onOrderCreated(Map<String, Object> payload) {
        try {
            OrderCreatedEvent event = objectMapper.convertValue(payload, OrderCreatedEvent.class);
            logger.info("Received OrderCreatedEvent for orderId={}", event.orderId());

            List<OrderItemEvent> items = event.items();
            inventoryService.reserveForOrder(event.orderId(), event.orderNumber(), items);
        } catch (Exception e) {
            logger.error("Error processing OrderCreatedEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume OrderCancelledEvent and release reserved inventory.
     * Requirements: 7.13
     */
    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "inventory-service")
    public void onOrderCancelled(Map<String, Object> payload) {
        try {
            OrderCreatedEvent event = objectMapper.convertValue(payload, OrderCreatedEvent.class);
            logger.info("Received OrderCancelledEvent for orderId={}", event.orderId());
            inventoryService.releaseForOrder(event.orderId(), event.items());
        } catch (Exception e) {
            logger.error("Error processing OrderCancelledEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume PaymentFailedEvent and release reserved inventory (saga compensation).
     * Requirements: 7.13
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "inventory-service")
    public void onPaymentFailed(Map<String, Object> payload) {
        try {
            // PaymentFailedEvent only carries orderId/orderNumber, not items.
            // We need to look up the order items — for now log and rely on
            // the OrderCancelledEvent that Order Service will publish.
            Long orderId = ((Number) payload.get("orderId")).longValue();
            logger.info("Received PaymentFailedEvent for orderId={} — awaiting OrderCancelledEvent for release", orderId);
        } catch (Exception e) {
            logger.error("Error processing PaymentFailedEvent: {}", e.getMessage(), e);
        }
    }
}
