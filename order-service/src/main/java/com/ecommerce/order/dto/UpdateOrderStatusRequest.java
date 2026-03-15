package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating order status.
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;
}
