package com.ecommerce.payment.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.payment.dto.*;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for payment operations.
 * Requirements: 9.1, 9.4, 9.5, 9.6
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /api/payments/process
     * Requirements: 9.1, 9.2, 9.3, 9.7
     */
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.processPayment(request)));
    }

    /**
     * GET /api/payments/{paymentId}
     * Requirements: 9.1
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentById(paymentId)));
    }

    /**
     * POST /api/payments/refund
     * Requirements: 9.6
     */
    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.refundPayment(request)));
    }

    /**
     * POST /api/payments/methods
     * Requirements: 9.4, 9.5
     */
    @PostMapping("/methods")
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> addPaymentMethod(
            @Valid @RequestBody AddPaymentMethodRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.addPaymentMethod(request)));
    }

    /**
     * GET /api/payments/user/{userId}/methods
     * Requirements: 9.4, 9.5
     */
    @GetMapping("/user/{userId}/methods")
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> getUserPaymentMethods(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getUserPaymentMethods(userId)));
    }
}
