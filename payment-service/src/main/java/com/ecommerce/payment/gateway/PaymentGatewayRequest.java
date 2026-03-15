package com.ecommerce.payment.gateway;

import java.math.BigDecimal;

/**
 * Abstraction for a payment gateway charge request.
 */
public record PaymentGatewayRequest(
        String gatewayToken,   // token representing the payment method
        BigDecimal amount,
        String currency,
        String idempotencyKey,  // orderId-based key to prevent duplicate charges
        String description
) {}
