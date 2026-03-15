package com.ecommerce.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * PaymentMethod entity storing encrypted card/payment data per user.
 * Card number is stored AES-256 encrypted. Requirements: 9.4, 9.5, 15.4
 */
@Entity
@Table(
    name = "payment_methods",
    indexes = {
        @Index(name = "idx_pm_user_id", columnList = "user_id"),
        @Index(name = "idx_pm_status", columnList = "status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentMethodType type;

    /** AES-256 encrypted card number. Requirements: 9.4, 15.4 */
    @Column(name = "encrypted_card_number", length = 512)
    private String encryptedCardNumber;

    /** Last 4 digits stored in plain text for display. */
    @Column(name = "last_four_digits", length = 4)
    private String lastFourDigits;

    @Column(name = "card_holder_name")
    private String cardHolderName;

    /** MM/YY expiry stored encrypted. */
    @Column(name = "encrypted_expiry", length = 256)
    private String encryptedExpiry;

    /** Token from payment gateway representing this payment method. */
    @Column(name = "gateway_token")
    private String gatewayToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PaymentMethodStatus status = PaymentMethodStatus.ACTIVE;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
