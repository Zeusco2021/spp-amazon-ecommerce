package com.ecommerce.common.event;

import java.time.LocalDateTime;

/**
 * Event published when a category is updated.
 * Requirements: 33.3
 */
public record CategoryUpdatedEvent(
        Long categoryId,
        String name,
        Long parentCategoryId,
        LocalDateTime updatedAt
) {}
