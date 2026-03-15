package com.ecommerce.order.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Asynchronous audit logging service for order-related events.
 * Requirements: 16.3, 16.7
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log an order creation event.
     * Req 16.3: userId, orderId, total amount
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderCreated(String userId, String orderId, BigDecimal totalAmount, String ipAddress) {
        String details = String.format("{\"orderId\":\"%s\",\"totalAmount\":\"%s\"}", orderId, totalAmount);
        persist(new AuditLog(userId, "ORDER_CREATED", "ORDER", orderId, details, ipAddress));
    }

    /**
     * Generic event logging for extensibility.
     * Req 16.7: userId, action, entityType, entityId, details, IP, timestamp
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logGenericEvent(String userId, String action, String entityType,
                                String entityId, String details, String ipAddress) {
        persist(new AuditLog(userId, action, entityType, entityId, details, ipAddress));
    }

    private void persist(AuditLog log) {
        try {
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to persist audit log: action={} userId={}", log.getAction(), log.getUserId(), e);
        }
    }
}
