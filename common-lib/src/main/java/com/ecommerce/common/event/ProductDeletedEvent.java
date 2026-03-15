package com.ecommerce.common.event;

import java.time.LocalDateTime;

/**
 * Event published when a product is deleted.
 * Requirements: 33.1, 33.2
 */
public record ProductDeletedEvent(
        Long productId,
        String sku,
        LocalDateTime deletedAt
) {}
