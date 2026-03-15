package com.ecommerce.product.cache;

import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.CacheTtl;
import com.ecommerce.common.cache.RedisCacheService;
import com.ecommerce.product.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Product-specific cache service integrating with RedisCacheService.
 * Implements cache-aside pattern for product data.
 * Requirements: 17.1, 17.2, 17.3, 28.1
 */
@Service
public class ProductCacheService {

    private static final Logger logger = LoggerFactory.getLogger(ProductCacheService.class);

    private final RedisCacheService cacheService;

    public ProductCacheService(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Get product from cache, or load from DB using cache-aside pattern.
     * Key pattern: product:{productId}
     * TTL: 1 hour
     * Requirements: 17.1, 17.2, 28.1
     */
    public ProductResponse getOrLoad(Long productId, Supplier<ProductResponse> loader) {
        String key = CacheKeyConstants.productKey(productId);
        return cacheService.getOrLoad(key, ProductResponse.class, CacheTtl.PRODUCT, loader);
    }

    /**
     * Get product from cache only (no DB fallback).
     * Requirements: 17.1
     */
    public ProductResponse getFromCache(Long productId) {
        String key = CacheKeyConstants.productKey(productId);
        return cacheService.getFromCache(key, ProductResponse.class);
    }

    /**
     * Store product in cache with 1-hour TTL.
     * Requirements: 17.2
     */
    public void cacheProduct(ProductResponse product) {
        String key = CacheKeyConstants.productKey(product.getId());
        cacheService.setInCache(key, product, CacheTtl.PRODUCT);
        logger.debug("Cached product: {}", product.getId());
    }

    /**
     * Invalidate product cache entry.
     * Called when product is updated or deleted.
     * Requirements: 17.3, 33.1
     */
    public void invalidateProduct(Long productId) {
        String key = CacheKeyConstants.productKey(productId);
        cacheService.invalidate(key);
        logger.debug("Invalidated product cache: {}", productId);
    }

    /**
     * Invalidate all search result caches that may contain this product.
     * Requirements: 33.2
     */
    public void invalidateSearchCaches() {
        cacheService.invalidatePattern(CacheKeyConstants.SEARCH_KEY_PREFIX + "*");
        logger.debug("Invalidated all search caches");
    }

    /**
     * Invalidate categories cache.
     * Requirements: 33.3
     */
    public void invalidateCategoriesCache() {
        cacheService.invalidate(CacheKeyConstants.CATEGORIES_KEY);
        logger.debug("Invalidated categories cache");
    }
}
