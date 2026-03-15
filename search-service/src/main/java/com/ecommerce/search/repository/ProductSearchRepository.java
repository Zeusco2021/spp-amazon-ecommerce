package com.ecommerce.search.repository;

import com.ecommerce.search.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Elasticsearch repository for product documents.
 * Requirements: 5.6, 5.9
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    /**
     * Find products by status with pagination.
     * Used to filter only ACTIVE products in search results.
     */
    Page<ProductDocument> findByStatus(String status, Pageable pageable);
}
