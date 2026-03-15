package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for payment operations.
 * Requirements: 9.1, 9.2, 9.3
 */
@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String transactionId;
    private String failureReason;
    private String gateway;
    private LocalDateTime createdAt;
}
