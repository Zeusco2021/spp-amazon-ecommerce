package com.ecommerce.common.event;

import java.time.LocalDateTime;

public record PaymentFailedEvent(
        Long orderId,
        String orderNumber,
        String reason,
        LocalDateTime occurredAt
) {}
