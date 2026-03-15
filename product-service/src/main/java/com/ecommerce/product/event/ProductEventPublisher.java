package com.ecommerce.product.event;

import com.ecommerce.common.event.ProductCreatedEvent;
import com.ecommerce.common.event.ProductDeletedEvent;
import com.ecommerce.common.event.ProductUpdatedEvent;
import com.ecommerce.common.kafka.KafkaTopics;
import com.ecommerce.product.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Publishes product lifecycle events to Kafka topics.
 * Requirements: 3.3, 5.9
 */
@Service
public class ProductEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProductEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish a ProductCreatedEvent when a product is created.
     */
    public void publishProductCreated(ProductResponse product) {
        ProductCreatedEvent event = new ProductCreatedEvent(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                product.getCategoryId(),
                product.getSellerId(),
                product.getStatus(),
                product.getCreatedAt()
        );
        kafkaTemplate.send(KafkaTopics.PRODUCT_CREATED, String.valueOf(product.getId()), event);
        logger.info("Published ProductCreatedEvent for productId: {}", product.getId());
    }

    /**
     * Publish a ProductUpdatedEvent when a product is updated.
     */
    public void publishProductUpdated(ProductResponse product) {
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getStatus(),
                product.getUpdatedAt() != null ? product.getUpdatedAt() : LocalDateTime.now()
        );
        kafkaTemplate.send(KafkaTopics.PRODUCT_UPDATED, String.valueOf(product.getId()), event);
        logger.info("Published ProductUpdatedEvent for productId: {}", product.getId());
    }

    /**
     * Publish a ProductDeletedEvent when a product is deleted.
     */
    public void publishProductDeleted(Long productId, String sku) {
        ProductDeletedEvent event = new ProductDeletedEvent(productId, sku, LocalDateTime.now());
        kafkaTemplate.send(KafkaTopics.PRODUCT_DELETED, String.valueOf(productId), event);
        logger.info("Published ProductDeletedEvent for productId: {}", productId);
    }
}
