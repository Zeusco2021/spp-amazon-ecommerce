package com.ecommerce.user.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous audit logging service for user-related events.
 * Requirements: 16.1, 16.2, 16.7
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a successful login event.
     * Req 16.1: userId, timestamp, IP
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginSuccess(String userId, String email, String ipAddress) {
        String details = String.format("{\"email\":\"%s\"}", email);
        persist(new AuditLog(userId, "LOGIN_SUCCESS", "USER", userId, details, ipAddress));
    }

    /**
     * Log a failed login attempt.
     * Req 16.2: email, timestamp, IP
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginFailed(String email, String ipAddress) {
        String details = String.format("{\"email\":\"%s\"}", email);
        persist(new AuditLog(null, "LOGIN_FAILED", "USER", null, details, ipAddress));
    }

    /**
     * Log a logout event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogout(String userId, String ipAddress) {
        persist(new AuditLog(userId, "LOGOUT", "USER", userId, null, ipAddress));
    }

    /**
     * Log a user registration event.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserRegistered(String userId, String email, String ipAddress) {
        String details = String.format("{\"email\":\"%s\"}", email);
        persist(new AuditLog(userId, "USER_REGISTERED", "USER", userId, details, ipAddress));
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
