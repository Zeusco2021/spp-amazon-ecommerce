package com.ecommerce.payment.dto;

import com.ecommerce.payment.entity.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for adding a payment method.
 * Requirements: 9.4, 9.5
 */
@Data
public class AddPaymentMethodRequest {

    @NotNull
    private Long userId;

    @NotNull
    private PaymentMethodType type;

    /** Raw card number — will be encrypted before storage. Requirements: 9.4, 15.4 */
    @NotBlank
    @Pattern(regexp = "^\\d{13,19}$", message = "Card number must be 13-19 digits")
    private String cardNumber;

    @NotBlank
    @Size(max = 100, message = "Card holder name must not exceed 100 characters")
    private String cardHolderName;

    /** MM/YY format */
    @NotBlank
    @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "Expiry must be in MM/YY format")
    private String expiry;

    private Boolean isDefault = false;
}
