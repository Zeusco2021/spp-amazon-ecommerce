package com.ecommerce.payment.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous audit logging service for payment-related events.
 * Requirements: 16.4, 16.7
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a payment processing event.
     * Req 16.4: paymentId, orderId, result
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPaymentProcessed(String userId, String paymentId, String orderId,
                                    String result, String ipAddress) {
        String details = String.format("{\"paymentId\":\"%s\",\"orderId\":\"%s\",\"result\":\"%s\"}",
                paymentId, orderId, result);
        persist(new AuditLog(userId, "PAYMENT_PROCESSED", "PAYMENT", paymentId, details, ipAddress));
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
