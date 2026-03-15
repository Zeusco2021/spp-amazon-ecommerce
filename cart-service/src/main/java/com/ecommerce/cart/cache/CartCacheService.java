package com.ecommerce.cart.cache;

import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.CacheTtl;
import com.ecommerce.common.cache.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Cart-specific cache service.
 * Implements dual-storage strategy: Redis for fast access, MySQL for persistence.
 * Requirements: 6.6, 6.7, 17.4, 28.2
 */
@Service
public class CartCacheService {

    private static final Logger logger = LoggerFactory.getLogger(CartCacheService.class);

    private final RedisCacheService cacheService;

    public CartCacheService(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Get cart from Redis cache.
     * Key pattern: cart:{userId}
     * Requirements: 17.4, 28.2
     */
    public CartResponse getFromCache(Long userId) {
        String key = CacheKeyConstants.cartKey(userId);
        return cacheService.getFromCache(key, CartResponse.class);
    }

    /**
     * Get cart from cache, or load from MySQL if Redis fails/misses.
     * Implements recovery strategy from MySQL when Redis fails.
     * Requirements: 6.6, 6.7, 17.4, 28.2
     */
    public CartResponse getOrLoad(Long userId, Supplier<CartResponse> mysqlLoader) {
        String key = CacheKeyConstants.cartKey(userId);
        try {
            CartResponse cached = cacheService.getFromCache(key, CartResponse.class);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            // Redis failure - fall through to MySQL
            logger.warn("Redis unavailable for cart:{}, falling back to MySQL", userId, e);
        }

        // Load from MySQL (fallback)
        CartResponse cart = mysqlLoader.get();
        if (cart != null) {
            // Try to re-populate cache (best-effort)
            try {
                cacheService.setInCache(key, cart, CacheTtl.CART);
            } catch (Exception e) {
                logger.warn("Failed to re-populate Redis cache for cart:{}", userId, e);
            }
        }
        return cart;
    }

    /**
     * Store cart in Redis cache with 7-day TTL.
     * Requirements: 6.6, 17.4
     */
    public void cacheCart(CartResponse cart) {
        String key = CacheKeyConstants.cartKey(cart.getUserId());
        cacheService.setInCache(key, cart, CacheTtl.CART);
        logger.debug("Cached cart for userId: {}", cart.getUserId());
    }

    /**
     * Invalidate cart cache for a user.
     * Called when an order is created from the cart.
     * Requirements: 28.3, 33.4
     */
    public void invalidateCart(Long userId) {
        String key = CacheKeyConstants.cartKey(userId);
        cacheService.invalidate(key);
        logger.debug("Invalidated cart cache for userId: {}", userId);
    }
}
