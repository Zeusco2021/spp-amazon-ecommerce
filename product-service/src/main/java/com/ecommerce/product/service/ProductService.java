package com.ecommerce.product.service;

import com.ecommerce.product.audit.AuditService;
import com.ecommerce.product.cache.ProductCacheService;
import com.ecommerce.product.dto.ProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.event.ProductEventPublisher;
import com.ecommerce.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for product CRUD operations.
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 5.9, 17.1, 17.2, 17.3
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;
    private final ProductEventPublisher productEventPublisher;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository,
                          ProductCacheService productCacheService,
                          ProductEventPublisher productEventPublisher,
                          AuditService auditService) {
        this.productRepository = productRepository;
        this.productCacheService = productCacheService;
        this.productEventPublisher = productEventPublisher;
        this.auditService = auditService;
    }

    /**
     * Create a new product.
     * Validates SKU uniqueness, price > 0, and discountPrice < price.
     * Assigns ACTIVE status by default.
     * Requirements: 3.1, 3.2, 3.4, 3.5, 3.6
     */
    public ProductResponse createProduct(ProductRequest request) {
        validateSkuUnique(request.getSku(), null);
        validatePrices(request);

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sku(request.getSku())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .stockQuantity(request.getStockQuantity())
                .categoryId(request.getCategoryId())
                .sellerId(request.getSellerId())
                .brand(request.getBrand())
                .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : new java.util.ArrayList<>())
                .status(Product.Status.ACTIVE)
                .build();

        Product saved = productRepository.save(product);
        ProductResponse response = toProductResponse(saved);
        productEventPublisher.publishProductCreated(response);
        return response;
    }

    /**
     * Get a product by ID.
     * Tries Redis cache first; falls back to MySQL and caches the result.
     * Requirements: 3.7, 17.1, 17.2
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return productCacheService.getOrLoad(id, () -> {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            return toProductResponse(product);
        });
    }

    /**
     * Update an existing product.
     * Validates SKU uniqueness if changed, price > 0, and discountPrice < price.
     * Invalidates cache after update and caches the new version.
     * Requirements: 3.1, 3.4, 3.5, 3.6, 17.3
     */
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Validate SKU uniqueness only if it changed
        if (!product.getSku().equals(request.getSku())) {
            validateSkuUnique(request.getSku(), id);
        }
        validatePrices(request);

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategoryId(request.getCategoryId());
        product.setSellerId(request.getSellerId());
        product.setBrand(request.getBrand());
        if (request.getImageUrls() != null) {
            product.setImageUrls(request.getImageUrls());
        }

        Product saved = productRepository.save(product);
        ProductResponse response = toProductResponse(saved);

        // Invalidate stale cache entry and store the updated version
        productCacheService.invalidateProduct(id);
        productCacheService.cacheProduct(response);

        productEventPublisher.publishProductUpdated(response);

        // Req 16.5: log product update event asynchronously
        String sellerId = saved.getSellerId() != null ? String.valueOf(saved.getSellerId()) : null;
        String changes = String.format("{\"name\":\"%s\",\"price\":\"%s\",\"sku\":\"%s\"}",
                saved.getName(), saved.getPrice(), saved.getSku());
        auditService.logProductUpdated(sellerId, String.valueOf(id), changes, null);

        return response;
    }

    /**
     * Delete a product by ID.
     * Removes from DB, invalidates cache, and publishes ProductDeletedEvent.
     * Requirements: 3.3, 5.9
     */
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        String sku = product.getSku();
        productRepository.delete(product);
        productCacheService.invalidateProduct(id);
        productEventPublisher.publishProductDeleted(id, sku);
    }

    // --- Private helpers ---

    private void validateSkuUnique(String sku, Long excludeId) {
        productRepository.findBySku(sku).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new RuntimeException("SKU already exists");
            }
        });
    }

    private void validatePrices(ProductRequest request) {
        if (request.getPrice() != null && request.getPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Price must be greater than zero");
        }
        if (request.getDiscountPrice() != null && request.getPrice() != null
                && request.getDiscountPrice().compareTo(request.getPrice()) >= 0) {
            throw new RuntimeException("Discount price must be less than price");
        }
    }

    private ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .discountPrice(product.getDiscountPrice())
                .stockQuantity(product.getStockQuantity())
                .categoryId(product.getCategoryId())
                .sellerId(product.getSellerId())
                .brand(product.getBrand())
                .imageUrls(product.getImageUrls())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
