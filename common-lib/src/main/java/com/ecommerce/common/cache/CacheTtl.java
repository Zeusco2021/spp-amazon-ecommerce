package com.ecommerce.common.cache;

import java.time.Duration;

/**
 * TTL (Time-To-Live) durations for different cache data types.
 * Requirements: 29.1-29.6
 */
public final class CacheTtl {

    private CacheTtl() {}

    /** TTL for product cache: product:{productId} — 1 hour (Req 29.1) */
    public static final Duration PRODUCT = Duration.ofHours(1);

    /** TTL for search results cache: search:{queryHash} — 30 minutes (Req 29.2) */
    public static final Duration SEARCH = Duration.ofMinutes(30);

    /** TTL for user session cache: session:{userId} — 24 hours (Req 29.3) */
    public static final Duration SESSION = Duration.ofHours(24);

    /** TTL for cart cache: cart:{userId} — 7 days (Req 29.4) */
    public static final Duration CART = Duration.ofDays(7);

    /** TTL for categories cache: categories:all — 24 hours (Req 29.5) */
    public static final Duration CATEGORIES = Duration.ofHours(24);

    /** TTL for recommendations cache: recommendations:{userId} — 2 hours (Req 29.6) */
    public static final Duration RECOMMENDATIONS = Duration.ofHours(2);
}
