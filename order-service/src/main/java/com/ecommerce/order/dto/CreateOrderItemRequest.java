package com.ecommerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for a single item in a create-order request.
 * Prices are provided by the caller (captured at time of purchase).
 * Requirements: 7.16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderItemRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotBlank(message = "productSku is required")
    private String productSku;

    @NotBlank(message = "productName is required")
    private String productName;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

    @NotNull
    @Positive(message = "unitPrice must be positive")
    private BigDecimal unitPrice;
}
