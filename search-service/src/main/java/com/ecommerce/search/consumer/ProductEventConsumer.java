package com.ecommerce.search.consumer;

import com.ecommerce.common.event.ProductCreatedEvent;
import com.ecommerce.common.event.ProductDeletedEvent;
import com.ecommerce.common.event.ProductUpdatedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.search.service.ProductIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for product events.
 * Keeps the Elasticsearch index in sync with product changes.
 * Requirements: 5.9
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ProductIndexService productIndexService;

    @KafkaListener(topics = KafkaTopics.PRODUCT_CREATED, groupId = "search-service")
    public void onProductCreated(ProductCreatedEvent event) {
        try {
            log.debug("Received ProductCreatedEvent for product {}", event.productId());
            productIndexService.indexProduct(event);
        } catch (Exception e) {
            log.error("Failed to index product {} from ProductCreatedEvent: {}", event.productId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.PRODUCT_UPDATED, groupId = "search-service")
    public void onProductUpdated(ProductUpdatedEvent event) {
        try {
            log.debug("Received ProductUpdatedEvent for product {}", event.productId());
            productIndexService.updateProduct(event);
        } catch (Exception e) {
            log.error("Failed to update product {} from ProductUpdatedEvent: {}", event.productId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.PRODUCT_DELETED, groupId = "search-service")
    public void onProductDeleted(ProductDeletedEvent event) {
        try {
            log.debug("Received ProductDeletedEvent for product {}", event.productId());
            productIndexService.deleteProduct(event.productId());
        } catch (Exception e) {
            log.error("Failed to delete product {} from ProductDeletedEvent: {}", event.productId(), e.getMessage(), e);
        }
    }
}
