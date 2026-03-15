package com.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for processing a payment.
 * Requirements: 9.1
 */
@Data
public class PaymentRequest {

    @NotNull
    private Long orderId;

    @NotBlank
    private String orderNumber;

    @NotNull
    private Long userId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank
    private String currency;

    /** ID of the stored payment method to charge. */
    @NotNull
    private Long paymentMethodId;
}
