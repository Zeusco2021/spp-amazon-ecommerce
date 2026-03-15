package com.ecommerce.payment.service;

import com.ecommerce.common.event.PaymentFailedEvent;
import com.ecommerce.common.event.PaymentSuccessEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.payment.audit.AuditService;
import com.ecommerce.payment.dto.*;
import com.ecommerce.payment.entity.*;
import com.ecommerce.payment.exception.PaymentMethodNotFoundException;
import com.ecommerce.payment.exception.PaymentNotFoundException;
import com.ecommerce.payment.gateway.PaymentGatewayClient;
import com.ecommerce.payment.gateway.PaymentGatewayRequest;
import com.ecommerce.payment.gateway.PaymentGatewayResponse;
import com.ecommerce.payment.repository.PaymentMethodRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.security.AesEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core payment processing service.
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7
 */
@Service
@Transactional
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentGatewayClient gatewayClient;
    private final AesEncryptionService encryptionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentMethodRepository paymentMethodRepository,
                          PaymentGatewayClient gatewayClient,
                          AesEncryptionService encryptionService,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.gatewayClient = gatewayClient;
        this.encryptionService = encryptionService;
        this.kafkaTemplate = kafkaTemplate;
        this.auditService = auditService;
    }

    /**
     * Process a payment for an order.
     * Communicates with gateway, stores result, publishes Kafka event.
     * Requirements: 9.1, 9.2, 9.3, 9.7
     */
    public PaymentResponse processPayment(PaymentRequest request) {
        PaymentMethod method = paymentMethodRepository
                .findByIdAndUserId(request.getPaymentMethodId(), request.getUserId())
                .orElseThrow(() -> new PaymentMethodNotFoundException(request.getPaymentMethodId()));

        // Build and persist a PENDING payment record for audit trail (Req 9.7)
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .orderNumber(request.getOrderNumber())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentMethodId(method.getId())
                .gateway(gatewayClient.gatewayName())
                .build();
        payment = paymentRepository.save(payment);

        // Charge via gateway
        PaymentGatewayRequest gatewayRequest = new PaymentGatewayRequest(
                method.getGatewayToken(),
                request.getAmount(),
                request.getCurrency(),
                "order-" + request.getOrderId(),
                "Order " + request.getOrderNumber()
        );
        PaymentGatewayResponse gatewayResponse = gatewayClient.charge(gatewayRequest);

        if (gatewayResponse.success()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(gatewayResponse.transactionId());
            logger.info("Payment completed: orderId={} transactionId={}", request.getOrderId(), gatewayResponse.transactionId());
            payment = paymentRepository.save(payment);
            publishPaymentSuccess(payment);
            // Req 16.4: log payment processing result
            auditService.logPaymentProcessed(
                    String.valueOf(request.getUserId()),
                    String.valueOf(payment.getId()),
                    String.valueOf(request.getOrderId()),
                    "COMPLETED", null);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.failureReason());
            logger.warn("Payment failed: orderId={} reason={}", request.getOrderId(), gatewayResponse.failureReason());
            payment = paymentRepository.save(payment);
            publishPaymentFailed(payment);
            // Req 16.4: log payment processing result
            auditService.logPaymentProcessed(
                    String.valueOf(request.getUserId()),
                    String.valueOf(payment.getId()),
                    String.valueOf(request.getOrderId()),
                    "FAILED", null);
        }

        return toResponse(payment);
    }

    /**
     * Get a payment by ID.
     * Requirements: 9.1
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        return toResponse(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId)));
    }

    /**
     * Refund a completed payment.
     * Requirements: 9.6
     */
    public PaymentResponse refundPayment(RefundRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(request.getPaymentId()));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalArgumentException("Only COMPLETED payments can be refunded");
        }

        PaymentGatewayResponse refundResponse = gatewayClient.refund(payment.getTransactionId(), request.getAmount());

        if (refundResponse.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            logger.info("Refund successful: paymentId={} transactionId={}", payment.getId(), refundResponse.transactionId());
        } else {
            throw new RuntimeException("Refund failed: " + refundResponse.failureReason());
        }

        return toResponse(paymentRepository.save(payment));
    }

    /**
     * Add a payment method for a user. Encrypts card data before storage.
     * Requirements: 9.4, 9.5, 15.4
     */
    public PaymentMethodResponse addPaymentMethod(AddPaymentMethodRequest request) {
        String lastFour = request.getCardNumber().replaceAll("\\s", "");
        lastFour = lastFour.substring(Math.max(0, lastFour.length() - 4));

        PaymentMethod method = PaymentMethod.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .encryptedCardNumber(encryptionService.encrypt(request.getCardNumber()))
                .lastFourDigits(lastFour)
                .cardHolderName(request.getCardHolderName())
                .encryptedExpiry(encryptionService.encrypt(request.getExpiry()))
                .gatewayToken("tok_" + java.util.UUID.randomUUID().toString().replace("-", ""))
                .isDefault(request.getIsDefault())
                .build();

        return toMethodResponse(paymentMethodRepository.save(method));
    }

    /**
     * Get all active payment methods for a user.
     * Requirements: 9.4, 9.5
     */
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> getUserPaymentMethods(Long userId) {
        return paymentMethodRepository.findByUserIdAndStatus(userId, PaymentMethodStatus.ACTIVE)
                .stream().map(this::toMethodResponse).collect(Collectors.toList());
    }

    /**
     * Create a pending payment record (called by Order Service before saga starts).
     * Requirements: 9.7
     */
    public PaymentResponse createPendingPayment(PaymentRequest request) {
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .orderNumber(request.getOrderNumber())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentMethodId(request.getPaymentMethodId())
                .gateway(gatewayClient.gatewayName())
                .build();
        return toResponse(paymentRepository.save(payment));
    }

    /**
     * Process an existing pending payment (triggered by InventoryReservedEvent in Saga).
     * Requirements: 7.8, 7.9, 7.10
     */
    public void processPaymentForSaga(PaymentRequest request, Long existingPaymentId) {
        Payment payment = paymentRepository.findById(existingPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(existingPaymentId));

        PaymentMethod method = paymentMethodRepository
                .findByIdAndUserId(request.getPaymentMethodId(), request.getUserId())
                .orElseThrow(() -> new PaymentMethodNotFoundException(request.getPaymentMethodId()));

        PaymentGatewayRequest gatewayRequest = new PaymentGatewayRequest(
                method.getGatewayToken(),
                request.getAmount(),
                request.getCurrency(),
                "order-" + request.getOrderId(),
                "Order " + request.getOrderNumber()
        );
        PaymentGatewayResponse gatewayResponse = gatewayClient.charge(gatewayRequest);

        if (gatewayResponse.success()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(gatewayResponse.transactionId());
            paymentRepository.save(payment);
            publishPaymentSuccess(payment);
            logger.info("Saga payment completed: orderId={}", request.getOrderId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.failureReason());
            paymentRepository.save(payment);
            publishPaymentFailed(payment);
            logger.warn("Saga payment failed: orderId={} reason={}", request.getOrderId(), gatewayResponse.failureReason());
        }
    }

    /** Expose repository for event consumer lookup. */
    public com.ecommerce.payment.repository.PaymentRepository getPaymentRepository() {
        return paymentRepository;
    }

    // --- Kafka publishing ---

    private void publishPaymentSuccess(Payment payment) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_SUCCESS,
                String.valueOf(payment.getOrderId()),
                new PaymentSuccessEvent(
                        payment.getOrderId(),
                        payment.getOrderNumber(),
                        payment.getId(),
                        payment.getTransactionId(),
                        payment.getAmount(),
                        LocalDateTime.now()
                ));
    }

    private void publishPaymentFailed(Payment payment) {
        kafkaTemplate.send(KafkaTopics.PAYMENT_FAILED,
                String.valueOf(payment.getOrderId()),
                new PaymentFailedEvent(
                        payment.getOrderId(),
                        payment.getOrderNumber(),
                        payment.getFailureReason(),
                        LocalDateTime.now()
                ));
    }

    // --- Mappers ---

    PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .orderId(p.getOrderId())
                .orderNumber(p.getOrderNumber())
                .userId(p.getUserId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .transactionId(p.getTransactionId())
                .failureReason(p.getFailureReason())
                .gateway(p.getGateway())
                .createdAt(p.getCreatedAt())
                .build();
    }

    PaymentMethodResponse toMethodResponse(PaymentMethod m) {
        return PaymentMethodResponse.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .type(m.getType())
                .lastFourDigits(m.getLastFourDigits())
                .cardHolderName(m.getCardHolderName())
                .status(m.getStatus())
                .isDefault(m.getIsDefault())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
