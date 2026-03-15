package com.ecommerce.common.event;

import java.time.LocalDateTime;

public record InventoryReservedEvent(
        Long orderId,
        String orderNumber,
        LocalDateTime reservedAt
) {}
