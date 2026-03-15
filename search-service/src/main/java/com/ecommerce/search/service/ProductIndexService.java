package com.ecommerce.search.service;

import com.ecommerce.common.event.ProductCreatedEvent;
import com.ecommerce.common.event.ProductUpdatedEvent;
import com.ecommerce.search.document.ProductDocument;
import com.ecommerce.search.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for indexing product documents in Elasticsearch.
 * Requirements: 5.9
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private final ProductSearchRepository productSearchRepository;

    /**
     * Index a new product in Elasticsearch from a ProductCreatedEvent.
     */
    public void indexProduct(ProductCreatedEvent event) {
        ProductDocument document = ProductDocument.builder()
                .id(String.valueOf(event.productId()))
                .name(event.name())
                .sku(event.sku())
                .price(event.price() != null ? event.price().doubleValue() : null)
                .categoryId(event.categoryId())
                .sellerId(event.sellerId())
                .status(event.status())
                .createdAt(event.createdAt())
                .build();

        productSearchRepository.save(document);
        log.info("Indexed product {} in Elasticsearch", event.productId());
    }

    /**
     * Update an existing product document in Elasticsearch from a ProductUpdatedEvent.
     */
    public void updateProduct(ProductUpdatedEvent event) {
        String id = String.valueOf(event.productId());
        productSearchRepository.findById(id).ifPresentOrElse(
                existing -> {
                    existing.setName(event.name());
                    existing.setSku(event.sku());
                    existing.setStatus(event.status());
                    productSearchRepository.save(existing);
                    log.info("Updated product {} in Elasticsearch", event.productId());
                },
                () -> log.warn("Product {} not found in Elasticsearch index, skipping update", event.productId())
        );
    }

    /**
     * Delete a product document from Elasticsearch by productId.
     */
    public void deleteProduct(Long productId) {
        String id = String.valueOf(productId);
        productSearchRepository.deleteById(id);
        log.info("Deleted product {} from Elasticsearch", productId);
    }
}
