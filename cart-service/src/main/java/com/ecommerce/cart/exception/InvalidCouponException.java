package com.ecommerce.cart.exception;

public class InvalidCouponException extends RuntimeException {
    public InvalidCouponException(String couponCode) {
        super("Invalid or expired coupon: " + couponCode);
    }
}
