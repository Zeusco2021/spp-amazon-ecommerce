package com.ecommerce.common.event;

import java.time.LocalDateTime;

public record ProductUpdatedEvent(
        Long productId,
        String sku,
        String name,
        String status,
        LocalDateTime updatedAt
) {}
