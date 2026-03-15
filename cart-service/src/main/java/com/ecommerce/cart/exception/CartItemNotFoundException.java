package com.ecommerce.cart.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long itemId) {
        super("Cart item not found: " + itemId);
    }
}
