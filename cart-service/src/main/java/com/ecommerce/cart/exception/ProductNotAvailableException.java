package com.ecommerce.cart.exception;

public class ProductNotAvailableException extends RuntimeException {
    public ProductNotAvailableException(Long productId) {
        super("Product not available: " + productId);
    }
}
