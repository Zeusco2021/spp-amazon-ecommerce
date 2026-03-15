package com.ecommerce.order.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AuditLog persistence.
 * Requirements: 16.6
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
