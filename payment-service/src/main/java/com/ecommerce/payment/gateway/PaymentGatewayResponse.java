package com.ecommerce.payment.gateway;

/**
 * Result returned by the payment gateway after a charge attempt.
 */
public record PaymentGatewayResponse(
        boolean success,
        String transactionId,
        String failureReason,
        String gateway
) {}
