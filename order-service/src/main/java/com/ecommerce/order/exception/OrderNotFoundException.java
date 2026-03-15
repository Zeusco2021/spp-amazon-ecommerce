package com.ecommerce.order.exception;

import com.ecommerce.common.exception.ResourceNotFoundException;

/**
 * Thrown when an order cannot be found.
 */
public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(Long orderId) {
        super("Order", orderId);
    }

    public OrderNotFoundException(String orderNumber) {
        super("Order not found with orderNumber: " + orderNumber);
    }
}
