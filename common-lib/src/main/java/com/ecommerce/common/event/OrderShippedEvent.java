package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when an order transitions to SHIPPED status.
 * Carries the tracking number for shipping notification emails.
 * Requirements: 12.2
 */
public record OrderShippedEvent(
        Long orderId,
        String orderNumber,
        Long userId,
        BigDecimal total,
        String trackingNumber,
        LocalDateTime shippedAt
) {}
