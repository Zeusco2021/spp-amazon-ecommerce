package com.ecommerce.payment.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit log entity for tracking payment events.
 * Requirements: 16.4, 16.6, 16.7
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "userId"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_expires_at", columnList = "expiresAt")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    @Column(nullable = false)
    private String action;

    private String entityType;

    private String entityId;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** Retention: 1 year from timestamp (Req 16.6) */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public AuditLog() {}

    public AuditLog(String userId, String action, String entityType, String entityId,
                    String details, String ipAddress) {
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.timestamp = LocalDateTime.now();
        this.expiresAt = this.timestamp.plusYears(1);
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
