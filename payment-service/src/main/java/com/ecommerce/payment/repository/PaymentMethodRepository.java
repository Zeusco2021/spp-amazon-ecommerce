package com.ecommerce.payment.repository;

import com.ecommerce.payment.entity.PaymentMethod;
import com.ecommerce.payment.entity.PaymentMethodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PaymentMethod entities.
 * Requirements: 9.4, 9.5
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByUserIdAndStatus(Long userId, PaymentMethodStatus status);

    Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId);
}
