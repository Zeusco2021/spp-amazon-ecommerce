package com.ecommerce.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Redis cache service implementing cache-aside pattern.
 * Requirements: 28.1-28.3, 33.1-33.9
 */
@Service
public class RedisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Metrics counters (Req 32.1-32.10)
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheErrorCounter;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheHitCounter = Counter.builder("redis.cache.hits")
                .description("Number of cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("redis.cache.misses")
                .description("Number of cache misses")
                .register(meterRegistry);
        this.cacheErrorCounter = Counter.builder("redis.cache.errors")
                .description("Number of cache errors")
                .register(meterRegistry);
    }

    /**
     * Retrieve a value from cache by key.
     * Returns null if not found or on error (fail-safe).
     * Requirements: 28.1, 28.6
     */
    public <T> T getFromCache(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                cacheHitCounter.increment();
                logger.debug("Cache HIT for key: {}", key);
                return objectMapper.convertValue(value, type);
            }
            cacheMissCounter.increment();
            logger.debug("Cache MISS for key: {}", key);
            return null;
        } catch (Exception e) {
            cacheErrorCounter.increment();
            logger.error("Error reading from cache key: {}", key, e);
            return null;
        }
    }

    /**
     * Store a value in cache with a TTL.
     * Requirements: 28.1, 29.1-29.6
     */
    public void setInCache(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            logger.debug("Stored in cache key: {} with TTL: {}", key, ttl);
        } catch (Exception e) {
            cacheErrorCounter.increment();
            logger.error("Error writing to cache key: {}", key, e);
        }
    }

    /**
     * Invalidate (delete) a specific cache key.
     * Requirements: 33.6, 28.3
     */
    public void invalidate(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            logger.debug("Invalidated cache key: {} (deleted={})", key, deleted);
        } catch (Exception e) {
            cacheErrorCounter.increment();
            logger.error("Error invalidating cache key: {}", key, e);
        }
    }

    /**
     * Invalidate all cache keys matching a pattern.
     * Uses Redis KEYS command to find matching keys, then DEL to remove them.
     * Requirements: 33.7, 33.8, 33.9
     *
     * NOTE: KEYS is O(N) - use with caution in production.
     * For large datasets, consider SCAN-based iteration.
     */
    public void invalidatePattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                logger.debug("Invalidated {} keys matching pattern: {}", deleted, pattern);
            } else {
                logger.debug("No keys found matching pattern: {}", pattern);
            }
        } catch (Exception e) {
            cacheErrorCounter.increment();
            logger.error("Error invalidating cache pattern: {}", pattern, e);
        }
    }

    /**
     * Cache-aside pattern: try cache first, load from source if missing, then cache the result.
     * Requirements: 28.1, 28.6
     *
     * @param key   cache key
     * @param type  expected return type
     * @param ttl   time-to-live for the cached value
     * @param loader function to load data from the source (e.g., database)
     * @return cached or freshly loaded value
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = getFromCache(key, type);
        if (cached != null) {
            return cached;
        }

        T loaded = loader.get();
        if (loaded != null) {
            setInCache(key, loaded, ttl);
        }
        return loaded;
    }

    /**
     * Check if a key exists in cache.
     */
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            cacheErrorCounter.increment();
            logger.error("Error checking cache key existence: {}", key, e);
            return false;
        }
    }

    /**
     * Refresh the TTL of an existing key without changing its value.
     */
    public void refreshTtl(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
            logger.debug("Refreshed TTL for cache key: {} to {}", key, ttl);
        } catch (Exception e) {
            cacheErrorCounter.increment();
            logger.error("Error refreshing TTL for cache key: {}", key, e);
        }
    }
}
