package com.ecommerce.product.cache;

import com.ecommerce.common.event.ProductUpdatedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka event listener for cache invalidation in Product Service.
 * Requirements: 28.7, 33.1-33.6
 */
@Component
public class ProductCacheEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ProductCacheEventListener.class);

    private final ProductCacheService productCacheService;

    public ProductCacheEventListener(ProductCacheService productCacheService) {
        this.productCacheService = productCacheService;
    }

    /**
     * Invalidate product cache when product is updated.
     * Requirements: 17.3, 33.1, 33.2
     */
    @KafkaListener(topics = KafkaTopics.PRODUCT_UPDATED, groupId = "product-cache-invalidation")
    public void onProductUpdated(ProductUpdatedEvent event) {
        logger.info("Received ProductUpdatedEvent for productId: {}", event.productId());
        productCacheService.invalidateProduct(event.productId());
        productCacheService.invalidateSearchCaches();
    }

    /**
     * Invalidate product cache when product is deleted.
     * Requirements: 17.3, 33.1, 33.2
     */
    @KafkaListener(topics = KafkaTopics.PRODUCT_DELETED, groupId = "product-cache-invalidation")
    public void onProductDeleted(ProductUpdatedEvent event) {
        logger.info("Received ProductDeletedEvent for productId: {}", event.productId());
        productCacheService.invalidateProduct(event.productId());
        productCacheService.invalidateSearchCaches();
    }
}
