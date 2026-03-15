package com.ecommerce.order.entity;

/**
 * Order lifecycle states.
 * Valid transitions: PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
 * Any state can transition to CANCELLED.
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
