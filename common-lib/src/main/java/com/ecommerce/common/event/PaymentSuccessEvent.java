package com.ecommerce.common.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentSuccessEvent(
        Long orderId,
        String orderNumber,
        Long paymentId,
        String transactionId,
        BigDecimal amount,
        LocalDateTime processedAt
) {}
