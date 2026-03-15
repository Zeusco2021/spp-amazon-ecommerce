package com.ecommerce.product.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous audit logging service for product-related events.
 * Requirements: 16.5, 16.7
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a product update event.
     * Req 16.5: productId, userId, changes made
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logProductUpdated(String userId, String productId, String changes, String ipAddress) {
        String details = String.format("{\"productId\":\"%s\",\"changes\":%s}", productId, changes);
        persist(new AuditLog(userId, "PRODUCT_UPDATED", "PRODUCT", productId, details, ipAddress));
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
