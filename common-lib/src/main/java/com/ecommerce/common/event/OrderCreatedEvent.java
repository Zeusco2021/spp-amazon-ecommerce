package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        String orderNumber,
        Long userId,
        List<OrderItemEvent> items,
        BigDecimal total,
        LocalDateTime createdAt
) {}
