package com.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for refunding a payment.
 * Requirements: 9.6
 */
@Data
public class RefundRequest {

    @NotNull
    private Long paymentId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String reason;
}
