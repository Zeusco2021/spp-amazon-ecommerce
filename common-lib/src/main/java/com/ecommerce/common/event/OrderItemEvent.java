package com.ecommerce.common.event;

import java.math.BigDecimal;

public record OrderItemEvent(
        Long productId,
        String productSku,
        Integer quantity,
        BigDecimal unitPrice
) {}
