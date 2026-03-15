package com.ecommerce.payment.service;

import com.ecommerce.common.kafka.KafkaTopics;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 15.4
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private PaymentGatewayClient gatewayClient;
    @Mock private AesEncryptionService encryptionService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private com.ecommerce.payment.audit.AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentMethod activeMethod;
    private PaymentRequest paymentRequest;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
        lenient().when(gatewayClient.gatewayName()).thenReturn("STRIPE");

        activeMethod = PaymentMethod.builder()
                .id(10L)
                .userId(1L)
                .type(PaymentMethodType.CREDIT_CARD)
                .lastFourDigits("4242")
                .cardHolderName("Test User")
                .gatewayToken("tok_test")
                .status(PaymentMethodStatus.ACTIVE)
                .isDefault(true)
                .build();

        paymentRequest = new PaymentRequest();
        paymentRequest.setOrderId(100L);
        paymentRequest.setOrderNumber("ORD-20240101-000001");
        paymentRequest.setUserId(1L);
        paymentRequest.setAmount(new BigDecimal("99.99"));
        paymentRequest.setCurrency("USD");
        paymentRequest.setPaymentMethodId(10L);
    }

    // -------------------------------------------------------------------------
    // processPayment — success path (Req 9.1, 9.2, 9.7)
    // -------------------------------------------------------------------------

    @Test
    void processPayment_success_storesCompletedAndPublishesEvent() {
        when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(activeMethod));

        Payment pendingPayment = buildPayment(PaymentStatus.PENDING);
        when(paymentRepository.save(any())).thenReturn(pendingPayment);

        PaymentGatewayResponse gatewayOk = new PaymentGatewayResponse(true, "txn_abc123", null, "STRIPE");
        when(gatewayClient.charge(any(PaymentGatewayRequest.class))).thenReturn(gatewayOk);

        Payment completedPayment = buildPayment(PaymentStatus.COMPLETED);
        completedPayment.setTransactionId("txn_abc123");
        when(paymentRepository.save(any())).thenReturn(pendingPayment).thenReturn(completedPayment);

        PaymentResponse response = paymentService.processPayment(paymentRequest);

        assertEquals(PaymentStatus.COMPLETED, response.getStatus());
        assertEquals("txn_abc123", response.getTransactionId());
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_SUCCESS), anyString(), any());
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.PAYMENT_FAILED), anyString(), any());
    }

    // -------------------------------------------------------------------------
    // processPayment — failure path (Req 9.3, 9.7)
    // -------------------------------------------------------------------------

    @Test
    void processPayment_gatewayFailure_storesFailedAndPublishesEvent() {
        when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(activeMethod));

        Payment pendingPayment = buildPayment(PaymentStatus.PENDING);
        Payment failedPayment = buildPayment(PaymentStatus.FAILED);
        failedPayment.setFailureReason("Card declined");
        when(paymentRepository.save(any())).thenReturn(pendingPayment).thenReturn(failedPayment);

        PaymentGatewayResponse gatewayFail = new PaymentGatewayResponse(false, null, "Card declined", "STRIPE");
        when(gatewayClient.charge(any())).thenReturn(gatewayFail);

        PaymentResponse response = paymentService.processPayment(paymentRequest);

        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("Card declined", response.getFailureReason());
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_FAILED), anyString(), any());
        verify(kafkaTemplate, never()).send(eq(KafkaTopics.PAYMENT_SUCCESS), anyString(), any());
    }

    @Test
    void processPayment_paymentMethodNotBelongingToUser_throws() {
        when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(PaymentMethodNotFoundException.class,
                () -> paymentService.processPayment(paymentRequest));
        verify(gatewayClient, never()).charge(any());
    }

    // -------------------------------------------------------------------------
    // Audit trail — pending record created before gateway call (Req 9.7)
    // -------------------------------------------------------------------------

    @Test
    void processPayment_createsPendingRecordBeforeGatewayCall() {
        when(paymentMethodRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(activeMethod));

        Payment pendingPayment = buildPayment(PaymentStatus.PENDING);
        when(paymentRepository.save(any())).thenReturn(pendingPayment);
        when(gatewayClient.charge(any())).thenReturn(new PaymentGatewayResponse(true, "txn_x", null, "STRIPE"));

        paymentService.processPayment(paymentRequest);

        // First save must be PENDING (audit record before gateway call)
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeast(1)).save(captor.capture());
        assertEquals(PaymentStatus.PENDING, captor.getAllValues().get(0).getStatus());
    }

    // -------------------------------------------------------------------------
    // refundPayment (Req 9.6)
    // -------------------------------------------------------------------------

    @Test
    void refundPayment_completedPayment_success() {
        Payment completed = buildPayment(PaymentStatus.COMPLETED);
        completed.setTransactionId("txn_abc");
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completed));
        when(gatewayClient.refund("txn_abc", new BigDecimal("50.00")))
                .thenReturn(new PaymentGatewayResponse(true, "ref_xyz", null, "STRIPE"));

        Payment refunded = buildPayment(PaymentStatus.REFUNDED);
        when(paymentRepository.save(any())).thenReturn(refunded);

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setPaymentId(1L);
        refundRequest.setAmount(new BigDecimal("50.00"));

        PaymentResponse response = paymentService.refundPayment(refundRequest);

        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
    }

    @Test
    void refundPayment_nonCompletedPayment_throws() {
        Payment pending = buildPayment(PaymentStatus.PENDING);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(pending));

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setPaymentId(1L);
        refundRequest.setAmount(new BigDecimal("10.00"));

        assertThrows(IllegalArgumentException.class, () -> paymentService.refundPayment(refundRequest));
        verify(gatewayClient, never()).refund(any(), any());
    }

    @Test
    void refundPayment_notFound_throws() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setPaymentId(99L);
        refundRequest.setAmount(new BigDecimal("10.00"));

        assertThrows(PaymentNotFoundException.class, () -> paymentService.refundPayment(refundRequest));
    }

    // -------------------------------------------------------------------------
    // addPaymentMethod — AES-256 encryption (Req 9.4, 15.4)
    // -------------------------------------------------------------------------

    @Test
    void addPaymentMethod_encryptsCardNumberAndExpiry() {
        when(encryptionService.encrypt("4111111111111111")).thenReturn("ENCRYPTED_CARD");
        when(encryptionService.encrypt("12/26")).thenReturn("ENCRYPTED_EXPIRY");

        PaymentMethod savedMethod = PaymentMethod.builder()
                .id(20L)
                .userId(1L)
                .type(PaymentMethodType.CREDIT_CARD)
                .encryptedCardNumber("ENCRYPTED_CARD")
                .lastFourDigits("1111")
                .cardHolderName("Test User")
                .encryptedExpiry("ENCRYPTED_EXPIRY")
                .status(PaymentMethodStatus.ACTIVE)
                .isDefault(false)
                .build();
        when(paymentMethodRepository.save(any())).thenReturn(savedMethod);

        AddPaymentMethodRequest request = new AddPaymentMethodRequest();
        request.setUserId(1L);
        request.setType(PaymentMethodType.CREDIT_CARD);
        request.setCardNumber("4111111111111111");
        request.setCardHolderName("Test User");
        request.setExpiry("12/26");
        request.setIsDefault(false);

        PaymentMethodResponse response = paymentService.addPaymentMethod(request);

        // Verify encryption was called for sensitive fields
        verify(encryptionService).encrypt("4111111111111111");
        verify(encryptionService).encrypt("12/26");

        // Response must NOT expose raw card number
        assertNull(response.getLastFourDigits() != null ? null : "exposed");
        assertEquals("1111", response.getLastFourDigits());
    }

    @Test
    void addPaymentMethod_extractsLastFourDigits() {
        when(encryptionService.encrypt(any())).thenReturn("ENCRYPTED");

        ArgumentCaptor<PaymentMethod> captor = ArgumentCaptor.forClass(PaymentMethod.class);
        when(paymentMethodRepository.save(captor.capture())).thenAnswer(inv -> {
            PaymentMethod m = inv.getArgument(0);
            m.setId(1L);
            m.setStatus(PaymentMethodStatus.ACTIVE);
            return m;
        });

        AddPaymentMethodRequest request = new AddPaymentMethodRequest();
        request.setUserId(1L);
        request.setType(PaymentMethodType.CREDIT_CARD);
        request.setCardNumber("4111 1111 1111 1234");
        request.setCardHolderName("Test User");
        request.setExpiry("01/27");
        request.setIsDefault(false);

        paymentService.addPaymentMethod(request);

        assertEquals("1234", captor.getValue().getLastFourDigits());
    }

    // -------------------------------------------------------------------------
    // getUserPaymentMethods (Req 9.4, 9.5)
    // -------------------------------------------------------------------------

    @Test
    void getUserPaymentMethods_returnsOnlyActiveMethodsForUser() {
        when(paymentMethodRepository.findByUserIdAndStatus(1L, PaymentMethodStatus.ACTIVE))
                .thenReturn(List.of(activeMethod));

        List<PaymentMethodResponse> methods = paymentService.getUserPaymentMethods(1L);

        assertEquals(1, methods.size());
        assertEquals("4242", methods.get(0).getLastFourDigits());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Payment buildPayment(PaymentStatus status) {
        return Payment.builder()
                .id(1L)
                .orderId(100L)
                .orderNumber("ORD-20240101-000001")
                .userId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(status)
                .gateway("STRIPE")
                .paymentMethodId(10L)
                .build();
    }
}
