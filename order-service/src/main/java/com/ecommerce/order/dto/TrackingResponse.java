package com.ecommerce.order.dto;

import com.ecommerce.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for order tracking information.
 * Requirements: 8.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingResponse {

    private Long orderId;
    private String orderNumber;
    private OrderStatus status;
    private String trackingNumber;
    private LocalDateTime estimatedDelivery;
}
