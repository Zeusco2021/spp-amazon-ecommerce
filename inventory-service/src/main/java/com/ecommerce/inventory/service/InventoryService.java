package com.ecommerce.inventory.service;

import com.ecommerce.common.event.InventoryReservedEvent;
import com.ecommerce.common.event.InventoryUnavailableEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.UpdateInventoryRequest;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.exception.InsufficientInventoryException;
import com.ecommerce.inventory.exception.InventoryNotFoundException;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Core inventory service handling stock operations.
 * Enforces the invariant: total = available + reserved (Requirement 10.6).
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */
@Service
@Transactional
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryService(InventoryRepository inventoryRepository,
                            KafkaTemplate<String, Object> kafkaTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Get inventory for a product.
     * Requirements: 10.1
     */
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        return toResponse(inventory);
    }

    /**
     * Set the available quantity for a product (creates record if absent).
     * Publishes INVENTORY_UPDATED event.
     * Requirements: 10.1, 10.5
     */
    public InventoryResponse updateInventory(Long productId, UpdateInventoryRequest request) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> Inventory.builder().productId(productId).build());

        inventory.setAvailableQuantity(request.getQuantity());
        Inventory saved = inventoryRepository.save(inventory);

        publishInventoryUpdated(saved);
        logger.info("Updated inventory for productId={}: available={}", productId, saved.getAvailableQuantity());
        return toResponse(saved);
    }

    /**
     * Reserve stock for a single product.
     * Decrements available, increments reserved.
     * Throws InsufficientInventoryException if not enough stock.
     * Requirements: 10.1, 10.2, 10.3
     */
    public InventoryResponse reserveStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new InsufficientInventoryException(productId, quantity, inventory.getAvailableQuantity());
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);

        Inventory saved = inventoryRepository.save(inventory);

        // Update product status if stock hits zero (Requirement 10.4)
        if (saved.getAvailableQuantity() == 0) {
            publishOutOfStock(productId);
        }

        publishInventoryUpdated(saved);
        return toResponse(saved);
    }

    /**
     * Release previously reserved stock back to available.
     * Requirements: 10.2, 10.3
     */
    public InventoryResponse releaseStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));

        int releaseAmount = Math.min(quantity, inventory.getReservedQuantity());
        inventory.setReservedQuantity(inventory.getReservedQuantity() - releaseAmount);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + releaseAmount);

        Inventory saved = inventoryRepository.save(inventory);
        publishInventoryUpdated(saved);
        logger.info("Released {}u for productId={}: available={}", releaseAmount, productId, saved.getAvailableQuantity());
        return toResponse(saved);
    }

    /**
     * Reserve inventory for all items in an order.
     * If any item fails, rolls back all reservations and publishes InventoryUnavailableEvent.
     * On success, publishes InventoryReservedEvent.
     * Requirements: 7.5, 7.6, 7.7
     */
    public void reserveForOrder(Long orderId, String orderNumber,
                                List<com.ecommerce.common.event.OrderItemEvent> items) {
        // Validate all items first before modifying anything
        for (var item : items) {
            Inventory inv = inventoryRepository.findByProductId(item.productId())
                    .orElse(null);
            if (inv == null || inv.getAvailableQuantity() < item.quantity()) {
                int available = inv != null ? inv.getAvailableQuantity() : 0;
                String reason = String.format("Insufficient stock for productId=%d: requested=%d, available=%d",
                        item.productId(), item.quantity(), available);
                logger.warn("Inventory unavailable for orderId={}: {}", orderId, reason);
                kafkaTemplate.send(KafkaTopics.INVENTORY_UNAVAILABLE,
                        String.valueOf(orderId),
                        new InventoryUnavailableEvent(orderId, orderNumber, reason, LocalDateTime.now()));
                return;
            }
        }

        // All items available — reserve them
        for (var item : items) {
            reserveStock(item.productId(), item.quantity());
        }

        logger.info("Inventory reserved for orderId={}", orderId);
        kafkaTemplate.send(KafkaTopics.INVENTORY_RESERVED,
                String.valueOf(orderId),
                new InventoryReservedEvent(orderId, orderNumber, LocalDateTime.now()));
    }

    /**
     * Release inventory for all items in a cancelled order.
     * Requirements: 7.13
     */
    public void releaseForOrder(Long orderId, List<com.ecommerce.common.event.OrderItemEvent> items) {
        for (var item : items) {
            try {
                releaseStock(item.productId(), item.quantity());
            } catch (Exception e) {
                logger.error("Failed to release inventory for productId={} on orderId={}: {}",
                        item.productId(), orderId, e.getMessage());
            }
        }
        logger.info("Inventory released for orderId={}", orderId);
    }

    // --- Private helpers ---

    private void publishInventoryUpdated(Inventory inventory) {
        kafkaTemplate.send(KafkaTopics.INVENTORY_UPDATED,
                String.valueOf(inventory.getProductId()),
                toResponse(inventory));
    }

    /**
     * Publish an event to update product status to OUT_OF_STOCK.
     * Requirements: 10.4
     */
    private void publishOutOfStock(Long productId) {
        logger.info("Product {} is now OUT_OF_STOCK", productId);
        // Publish a ProductUpdatedEvent-style signal via INVENTORY_UPDATED
        // The product-service listens and updates status accordingly
        kafkaTemplate.send(KafkaTopics.INVENTORY_UPDATED,
                String.valueOf(productId),
                java.util.Map.of("productId", productId, "status", "OUT_OF_STOCK"));
    }

    InventoryResponse toResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .totalQuantity(inventory.getTotalQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
