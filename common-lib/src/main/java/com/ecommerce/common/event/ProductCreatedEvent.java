package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a new product is created.
 * Requirements: 3.3, 5.9
 */
public record ProductCreatedEvent(
        Long productId,
        String name,
        String sku,
        BigDecimal price,
        Long categoryId,
        Long sellerId,
        String status,
        LocalDateTime createdAt
) {}
