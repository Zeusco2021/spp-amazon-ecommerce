package com.ecommerce.payment.event;

import com.ecommerce.common.event.InventoryReservedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.payment.dto.PaymentRequest;
import com.ecommerce.payment.entity.PaymentMethodStatus;
import com.ecommerce.payment.repository.PaymentMethodRepository;
import com.ecommerce.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumes inventory events and triggers payment processing as part of the Saga.
 *
 * - InventoryReservedEvent → process payment, publish PaymentSuccessEvent or PaymentFailedEvent
 *
 * Requirements: 7.8, 7.9, 7.10
 */
@Component
public class InventoryEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final PaymentService paymentService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(PaymentService paymentService,
                                  PaymentMethodRepository paymentMethodRepository,
                                  ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.paymentMethodRepository = paymentMethodRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * When inventory is reserved, proceed with payment processing.
     * The event carries orderId/orderNumber; we look up the pending payment record
     * created by the Order Service (or use the userId's default payment method).
     * Requirements: 7.8, 7.9, 7.10
     */
    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "payment-service")
    public void onInventoryReserved(Map<String, Object> payload) {
        try {
            InventoryReservedEvent event = objectMapper.convertValue(payload, InventoryReservedEvent.class);
            logger.info("Received InventoryReservedEvent for orderId={}", event.orderId());

            // Look up the pending payment for this order (created by Order Service via REST or event)
            paymentService.getPaymentRepository()
                    .findByOrderId(event.orderId())
                    .ifPresentOrElse(
                            payment -> {
                                // Payment record already exists (created by Order Service) — process it
                                PaymentRequest request = new PaymentRequest();
                                request.setOrderId(payment.getOrderId());
                                request.setOrderNumber(payment.getOrderNumber());
                                request.setUserId(payment.getUserId());
                                request.setAmount(payment.getAmount());
                                request.setCurrency(payment.getCurrency());
                                request.setPaymentMethodId(payment.getPaymentMethodId());
                                paymentService.processPaymentForSaga(request, payment.getId());
                            },
                            () -> logger.warn("No pending payment found for orderId={}", event.orderId())
                    );
        } catch (Exception e) {
            logger.error("Error processing InventoryReservedEvent: {}", e.getMessage(), e);
        }
    }
}
