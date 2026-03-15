package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.PaymentMethodStatus;
import com.ecommerce.payment.entity.PaymentMethodType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for payment method — never exposes encrypted card data.
 * Requirements: 9.4, 9.5
 */
@Data
@Builder
public class PaymentMethodResponse {
    private Long id;
    private Long userId;
    private PaymentMethodType type;
    private String lastFourDigits;
    private String cardHolderName;
    private PaymentMethodStatus status;
    private Boolean isDefault;
    private LocalDateTime createdAt;
}
