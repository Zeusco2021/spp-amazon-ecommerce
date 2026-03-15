package com.ecommerce.payment.gateway;

/**
 * Abstraction over external payment gateways (Stripe, PayPal).
 * Requirements: 9.1, 9.6
 */
public interface PaymentGatewayClient {

    /**
     * Charge the given payment method.
     * Requirements: 9.1
     */
    PaymentGatewayResponse charge(PaymentGatewayRequest request);

    /**
     * Refund a previously completed transaction.
     * Requirements: 9.6
     */
    PaymentGatewayResponse refund(String transactionId, java.math.BigDecimal amount);

    /**
     * Name of the gateway (e.g. "STRIPE").
     */
    String gatewayName();
}
