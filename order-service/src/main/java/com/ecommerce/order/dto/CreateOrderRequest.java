package com.ecommerce.order.dto;

import com.ecommerce.order.entity.ShippingAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for creating a new order.
 * Requirements: 7.1, 7.14, 7.16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<CreateOrderItemRequest> items;

    @NotNull(message = "shippingAddress is required")
    private ShippingAddress shippingAddress;

    /** Optional payment method ID. */
    private Long paymentMethodId;

    /** Optional discount amount. Defaults to ZERO if null. */
    private BigDecimal discount;
}
