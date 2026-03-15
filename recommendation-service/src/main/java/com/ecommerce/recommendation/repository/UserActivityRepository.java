package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.entity.UserActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    List<UserActivity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserActivity> findByUserIdAndActivityTypeOrderByCreatedAtDesc(Long userId, UserActivity.ActivityType type);

    List<UserActivity> findByProductIdAndActivityType(Long productId, UserActivity.ActivityType type);

    @Query("SELECT DISTINCT a.userId FROM UserActivity a WHERE a.productId = :productId AND a.activityType = 'PURCHASE'")
    List<Long> findUserIdsByProductIdAndPurchase(@Param("productId") Long productId);

    @Query("SELECT a.productId, COUNT(a) as cnt FROM UserActivity a WHERE a.activityType = 'PURCHASE' AND a.createdAt >= :since GROUP BY a.productId ORDER BY cnt DESC")
    List<Object[]> findTrendingProductIds(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT DISTINCT a.productId FROM UserActivity a WHERE a.userId = :userId AND a.activityType = 'PURCHASE'")
    List<Long> findPurchasedProductIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT a.productId FROM UserActivity a WHERE a.userId = :userId")
    List<Long> findInteractedProductIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT a2.productId, COUNT(a2) as cnt FROM UserActivity a1 JOIN UserActivity a2 ON a1.userId = a2.userId AND a2.productId != a1.productId WHERE a1.productId = :productId AND a1.activityType = 'PURCHASE' AND a2.activityType = 'PURCHASE' GROUP BY a2.productId ORDER BY cnt DESC")
    List<Object[]> findFrequentlyBoughtTogetherProductIds(@Param("productId") Long productId, Pageable pageable);
}
