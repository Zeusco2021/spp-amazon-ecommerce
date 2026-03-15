package com.ecommerce.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stripe payment gateway client.
 * In production this would use the Stripe Java SDK.
 * Requirements: 9.1, 9.6
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe", matchIfMissing = true)
public class StripeGatewayClient implements PaymentGatewayClient {

    private static final Logger logger = LoggerFactory.getLogger(StripeGatewayClient.class);

    @Value("${payment.stripe.api-key:sk_test_placeholder}")
    private String apiKey;

    @Override
    public PaymentGatewayResponse charge(PaymentGatewayRequest request) {
        logger.info("Stripe charge: amount={} currency={} idempotencyKey={}",
                request.amount(), request.currency(), request.idempotencyKey());
        try {
            // In production: Stripe.apiKey = apiKey; PaymentIntent.create(params)
            // Simulated successful charge for integration purposes
            String transactionId = "stripe_txn_" + UUID.randomUUID().toString().replace("-", "");
            return new PaymentGatewayResponse(true, transactionId, null, gatewayName());
        } catch (Exception e) {
            logger.error("Stripe charge failed: {}", e.getMessage());
            return new PaymentGatewayResponse(false, null, e.getMessage(), gatewayName());
        }
    }

    @Override
    public PaymentGatewayResponse refund(String transactionId, BigDecimal amount) {
        logger.info("Stripe refund: transactionId={} amount={}", transactionId, amount);
        try {
            // In production: Refund.create(params)
            String refundId = "stripe_ref_" + UUID.randomUUID().toString().replace("-", "");
            return new PaymentGatewayResponse(true, refundId, null, gatewayName());
        } catch (Exception e) {
            logger.error("Stripe refund failed: {}", e.getMessage());
            return new PaymentGatewayResponse(false, null, e.getMessage(), gatewayName());
        }
    }

    @Override
    public String gatewayName() {
        return "STRIPE";
    }
}
