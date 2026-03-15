package com.ecommerce.common.cache;

import com.ecommerce.common.event.CategoryUpdatedEvent;
import com.ecommerce.common.event.ProductDeletedEvent;
import com.ecommerce.common.event.ProductUpdatedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka-based cache invalidation event listener.
 * Consumes product and category events to maintain cache consistency.
 * Requirements: 28.7, 33.1-33.6
 */
@Component
@ConditionalOnBean(RedisCacheService.class)
public class CacheInvalidationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationEventListener.class);

    private final RedisCacheService cacheService;

    public CacheInvalidationEventListener(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Invalidate product cache when product is updated.
     * Also invalidates search caches that may contain the product.
     * Requirements: 33.1, 33.2
     */
    @KafkaListener(topics = KafkaTopics.PRODUCT_UPDATED, groupId = "cache-invalidation-group")
    public void onProductUpdated(ProductUpdatedEvent event) {
        logger.info("Cache invalidation triggered by ProductUpdatedEvent for productId: {}", event.productId());

        // Invalidate specific product cache (Req 33.1)
        cacheService.invalidate(CacheKeyConstants.productKey(event.productId()));

        // Invalidate all search caches that may contain this product (Req 33.2)
        cacheService.invalidatePattern(CacheKeyConstants.SEARCH_KEY_PREFIX + "*");
    }

    /**
     * Invalidate product cache when product is deleted.
     * Requirements: 33.1, 33.2
     */
    @KafkaListener(topics = KafkaTopics.PRODUCT_DELETED, groupId = "cache-invalidation-group")
    public void onProductDeleted(ProductDeletedEvent event) {
        logger.info("Cache invalidation triggered by ProductDeletedEvent for productId: {}", event.productId());

        // Invalidate specific product cache (Req 33.1)
        cacheService.invalidate(CacheKeyConstants.productKey(event.productId()));

        // Invalidate all search caches (Req 33.2)
        cacheService.invalidatePattern(CacheKeyConstants.SEARCH_KEY_PREFIX + "*");
    }

    /**
     * Invalidate categories cache when a category is updated.
     * Requirements: 33.3
     */
    @KafkaListener(topics = KafkaTopics.CATEGORY_UPDATED, groupId = "cache-invalidation-group")
    public void onCategoryUpdated(CategoryUpdatedEvent event) {
        logger.info("Cache invalidation triggered by CategoryUpdatedEvent for categoryId: {}", event.categoryId());

        // Invalidate categories cache (Req 33.3)
        cacheService.invalidate(CacheKeyConstants.CATEGORIES_KEY);

        // Also invalidate search caches as category changes affect search results
        cacheService.invalidatePattern(CacheKeyConstants.SEARCH_KEY_PREFIX + "*");
    }
}
