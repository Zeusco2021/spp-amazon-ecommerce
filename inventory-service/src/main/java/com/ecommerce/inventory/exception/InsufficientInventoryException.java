package com.ecommerce.inventory.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(Long productId, int requested, int available) {
        super(String.format(
            "Insufficient inventory for productId %d: requested=%d, available=%d",
            productId, requested, available
        ));
    }
}
