package com.ecommerce.recommendation.event;

import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.RedisCacheService;
import com.ecommerce.common.event.OrderCreatedEvent;
import com.ecommerce.common.event.OrderItemEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.recommendation.entity.UserActivity;
import com.ecommerce.recommendation.entity.UserPreference;
import com.ecommerce.recommendation.repository.UserActivityRepository;
import com.ecommerce.recommendation.repository.UserPreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka consumer for user activity events.
 * Tracks purchases and views to update user preferences for recommendations.
 * Requirements: 11.7
 */
@Component
public class UserActivityEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(UserActivityEventConsumer.class);
    private static final double PURCHASE_SCORE_INCREMENT = 2.0;
    private static final double VIEW_SCORE_INCREMENT = 0.5;

    private final UserActivityRepository userActivityRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final RedisCacheService cacheService;
    private final ObjectMapper objectMapper;

    public UserActivityEventConsumer(UserActivityRepository userActivityRepository,
                                     UserPreferenceRepository userPreferenceRepository,
                                     RedisCacheService cacheService,
                                     ObjectMapper objectMapper) {
        this.userActivityRepository = userActivityRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes order.confirmed events to record purchase activity and update category preferences.
     * Each item in the order is recorded as a PURCHASE activity.
     * Category preference scores are incremented by PURCHASE_SCORE_INCREMENT.
     * Recommendation cache is invalidated so next request gets fresh recommendations.
     * Requirements: 11.7
     */
    @KafkaListener(topics = KafkaTopics.ORDER_CONFIRMED, groupId = "recommendation-service")
    @Transactional
    public void onOrderConfirmed(Map<String, Object> payload) {
        try {
            OrderCreatedEvent event = objectMapper.convertValue(payload, OrderCreatedEvent.class);
            logger.info("Processing order.confirmed for userId={}, orderId={}", event.userId(), event.orderId());

            if (event.items() == null) return;

            for (OrderItemEvent item : event.items()) {
                // Record purchase activity
                UserActivity activity = UserActivity.builder()
                        .userId(event.userId())
                        .productId(item.productId())
                        .activityType(UserActivity.ActivityType.PURCHASE)
                        .build();
                userActivityRepository.save(activity);
            }

            // Invalidate recommendations cache so next request is fresh
            cacheService.invalidate(CacheKeyConstants.recommendationsKey(event.userId()));
            logger.debug("Recorded {} purchase activities for userId={}", event.items().size(), event.userId());

        } catch (Exception e) {
            logger.error("Error processing order.confirmed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Consumes user.activity events to record product view activity and update category preferences.
     * Expected payload fields: userId (Long), productId (Long), categoryId (Long), activityType (String)
     * Requirements: 11.7
     */
    @KafkaListener(topics = KafkaTopics.USER_ACTIVITY, groupId = "recommendation-service")
    @Transactional
    public void onUserActivity(Map<String, Object> payload) {
        try {
            Long userId = extractLong(payload, "userId");
            Long productId = extractLong(payload, "productId");
            Long categoryId = extractLong(payload, "categoryId");
            String activityTypeStr = extractString(payload, "activityType");

            if (userId == null || productId == null) {
                logger.warn("Skipping user.activity event with missing userId or productId");
                return;
            }

            UserActivity.ActivityType activityType = parseActivityType(activityTypeStr);

            UserActivity activity = UserActivity.builder()
                    .userId(userId)
                    .productId(productId)
                    .categoryId(categoryId)
                    .activityType(activityType)
                    .build();
            userActivityRepository.save(activity);

            // Update category preference score
            if (categoryId != null) {
                double increment = activityType == UserActivity.ActivityType.PURCHASE
                        ? PURCHASE_SCORE_INCREMENT : VIEW_SCORE_INCREMENT;
                updateCategoryPreference(userId, categoryId, increment);
            }

            logger.debug("Recorded {} activity for userId={}, productId={}", activityType, userId, productId);

        } catch (Exception e) {
            logger.error("Error processing user.activity event: {}", e.getMessage(), e);
        }
    }

    /**
     * Upserts a UserPreference record, incrementing the score for the given category.
     */
    private void updateCategoryPreference(Long userId, Long categoryId, double increment) {
        UserPreference preference = userPreferenceRepository
                .findByUserIdAndCategoryId(userId, categoryId)
                .orElseGet(() -> UserPreference.builder()
                        .userId(userId)
                        .categoryId(categoryId)
                        .score(0.0)
                        .build());

        preference.setScore(preference.getScore() + increment);
        preference.setLastUpdated(LocalDateTime.now());
        userPreferenceRepository.save(preference);
    }

    private UserActivity.ActivityType parseActivityType(String value) {
        if (value == null) return UserActivity.ActivityType.VIEW;
        try {
            return UserActivity.ActivityType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UserActivity.ActivityType.VIEW;
        }
    }

    private Long extractLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (Exception e) { return null; }
    }

    private String extractString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }
}
