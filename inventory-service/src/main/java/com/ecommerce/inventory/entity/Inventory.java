package com.ecommerce.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Inventory entity tracking available and reserved stock per product.
 * Invariant: total = available + reserved (Requirement 10.6)
 * Requirements: 10.1, 10.2
 */
@Entity
@Table(
    name = "inventory",
    indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id", unique = true)
    }
)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The product this inventory record belongs to.
     * Requirements: 10.1, 10.2
     */
    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    /**
     * Units available for new reservations.
     * Must never be negative (Requirement 10.3).
     */
    @Column(name = "available_quantity", nullable = false)
    @Builder.Default
    private Integer availableQuantity = 0;

    /**
     * Units currently reserved by pending/confirmed orders.
     */
    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Total stock = available + reserved.
     * Requirement 10.6
     */
    public int getTotalQuantity() {
        return availableQuantity + reservedQuantity;
    }
}
