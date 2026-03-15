package com.ecommerce.common.cache;

/**
 * Cache key patterns and TTL constants for Redis cache.
 * Requirements: 29.1-29.13
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {}

    // Key patterns (Req 29.8-29.13)
    public static final String PRODUCT_KEY_PREFIX = "product:";
    public static final String SEARCH_KEY_PREFIX = "search:";
    public static final String SESSION_KEY_PREFIX = "session:";
    public static final String CART_KEY_PREFIX = "cart:";
    public static final String CATEGORIES_KEY = "categories:all";
    public static final String RECOMMENDATIONS_KEY_PREFIX = "recommendations:";

    // TTL in seconds (Req 29.1-29.6)
    public static final long PRODUCT_TTL_SECONDS = 3600L;           // 1 hour
    public static final long SEARCH_TTL_SECONDS = 1800L;            // 30 minutes
    public static final long SESSION_TTL_SECONDS = 86400L;          // 24 hours
    public static final long CART_TTL_SECONDS = 604800L;            // 7 days
    public static final long CATEGORIES_TTL_SECONDS = 86400L;       // 24 hours
    public static final long RECOMMENDATIONS_TTL_SECONDS = 7200L;   // 2 hours

    /**
     * Build product cache key.
     */
    public static String productKey(Long productId) {
        return PRODUCT_KEY_PREFIX + productId;
    }

    /**
     * Build search cache key.
     */
    public static String searchKey(String queryHash) {
        return SEARCH_KEY_PREFIX + queryHash;
    }

    /**
     * Build session cache key.
     */
    public static String sessionKey(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    /**
     * Build cart cache key.
     */
    public static String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    /**
     * Build recommendations cache key.
     */
    public static String recommendationsKey(Long userId) {
        return RECOMMENDATIONS_KEY_PREFIX + userId;
    }
}
