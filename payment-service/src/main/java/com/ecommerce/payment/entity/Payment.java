package com.ecommerce.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment entity representing a payment transaction.
 * Requirements: 9.1, 9.2, 9.3, 9.7
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_user_id", columnList = "user_id"),
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_transaction_id", columnList = "transaction_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    /** External transaction ID from payment gateway (Stripe/PayPal). Requirements: 9.2 */
    @Column(name = "transaction_id")
    private String transactionId;

    /** Payment method used for this transaction. */
    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    /** Reason for failure when status is FAILED. Requirements: 9.3 */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Gateway used (STRIPE, PAYPAL). */
    @Column(name = "gateway", length = 20)
    private String gateway;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
