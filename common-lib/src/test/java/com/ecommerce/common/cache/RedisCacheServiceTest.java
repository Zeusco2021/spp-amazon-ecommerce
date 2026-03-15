package com.ecommerce.common.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisCacheService.
 * Requirements: 27-33
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private RedisCacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cacheService = new RedisCacheService(
                redisTemplate,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                new SimpleMeterRegistry()
        );
    }

    // =========================================================
    // Cache-aside pattern tests (Req 28.1, 28.6)
    // =========================================================

    @Test
    void getOrLoad_cacheHit_returnsFromCacheWithoutCallingLoader() {
        // Given
        String key = "product:1";
        String cachedValue = "cached-product";
        when(valueOps.get(key)).thenReturn(cachedValue);

        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Supplier<String> loader = () -> {
            loaderCallCount.incrementAndGet();
            return "db-product";
        };

        // When
        String result = cacheService.getOrLoad(key, String.class, Duration.ofHours(1), loader);

        // Then
        assertThat(result).isEqualTo(cachedValue);
        assertThat(loaderCallCount.get()).isZero(); // loader should NOT be called on cache hit
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void getOrLoad_cacheMiss_loadsFromSourceAndCaches() {
        // Given
        String key = "product:2";
        String dbValue = "db-product";
        when(valueOps.get(key)).thenReturn(null); // cache miss

        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Supplier<String> loader = () -> {
            loaderCallCount.incrementAndGet();
            return dbValue;
        };

        // When
        String result = cacheService.getOrLoad(key, String.class, Duration.ofHours(1), loader);

        // Then
        assertThat(result).isEqualTo(dbValue);
        assertThat(loaderCallCount.get()).isEqualTo(1); // loader called once
        verify(valueOps).set(eq(key), eq(dbValue), eq(Duration.ofHours(1))); // stored in cache
    }

    @Test
    void getOrLoad_cacheMissAndLoaderReturnsNull_doesNotCache() {
        // Given
        String key = "product:999";
        when(valueOps.get(key)).thenReturn(null);

        // When
        String result = cacheService.getOrLoad(key, String.class, Duration.ofHours(1), () -> null);

        // Then
        assertThat(result).isNull();
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    // =========================================================
    // Cache invalidation tests (Req 33.6, 33.7, 33.8, 33.9)
    // =========================================================

    @Test
    void invalidate_deletesSpecificKey() {
        // Given
        String key = "product:42";
        when(redisTemplate.delete(key)).thenReturn(true);

        // When
        cacheService.invalidate(key);

        // Then
        verify(redisTemplate).delete(key);
    }

    @Test
    void invalidatePattern_deletesAllMatchingKeys() {
        // Given
        String pattern = "search:*";
        Set<String> matchingKeys = Set.of("search:abc123", "search:def456", "search:ghi789");
        when(redisTemplate.keys(pattern)).thenReturn(matchingKeys);
        when(redisTemplate.delete(matchingKeys)).thenReturn(3L);

        // When
        cacheService.invalidatePattern(pattern);

        // Then
        verify(redisTemplate).keys(pattern);
        verify(redisTemplate).delete(matchingKeys);
    }

    @Test
    void invalidatePattern_noMatchingKeys_doesNotCallDelete() {
        // Given
        String pattern = "search:*";
        when(redisTemplate.keys(pattern)).thenReturn(Set.of());

        // When
        cacheService.invalidatePattern(pattern);

        // Then
        verify(redisTemplate, never()).delete(anyCollection());
    }

    // =========================================================
    // Error handling / Redis failover tests (Req 28.1)
    // =========================================================

    @Test
    void getFromCache_redisThrowsException_returnsNull() {
        // Given
        String key = "product:1";
        when(valueOps.get(key)).thenThrow(new RuntimeException("Redis connection failed"));

        // When
        String result = cacheService.getFromCache(key, String.class);

        // Then - should return null gracefully (fail-safe)
        assertThat(result).isNull();
    }

    @Test
    void setInCache_redisThrowsException_doesNotPropagateException() {
        // Given
        String key = "product:1";
        doThrow(new RuntimeException("Redis write failed"))
                .when(valueOps).set(anyString(), any(), any(Duration.class));

        // When / Then - should not throw
        cacheService.setInCache(key, "value", Duration.ofHours(1));
    }

    @Test
    void invalidate_redisThrowsException_doesNotPropagateException() {
        // Given
        String key = "product:1";
        when(redisTemplate.delete(key)).thenThrow(new RuntimeException("Redis delete failed"));

        // When / Then - should not throw
        cacheService.invalidate(key);
    }

    @Test
    void getOrLoad_redisThrowsException_fallsBackToLoader() {
        // Given
        String key = "product:1";
        String dbValue = "db-product";
        when(valueOps.get(key)).thenThrow(new RuntimeException("Redis unavailable"));

        // When
        String result = cacheService.getOrLoad(key, String.class, Duration.ofHours(1), () -> dbValue);

        // Then - should return DB value even when Redis fails
        assertThat(result).isEqualTo(dbValue);
    }

    // =========================================================
    // TTL tests (Req 29.1-29.6)
    // =========================================================

    @Test
    void setInCache_usesCorrectTtlForProducts() {
        // Given
        String key = CacheKeyConstants.productKey(1L);
        String value = "product-data";

        // When
        cacheService.setInCache(key, value, CacheTtl.PRODUCT);

        // Then
        verify(valueOps).set(eq(key), eq(value), eq(Duration.ofHours(1)));
    }

    @Test
    void setInCache_usesCorrectTtlForSearchResults() {
        // Given
        String key = CacheKeyConstants.searchKey("abc123");
        String value = "search-results";

        // When
        cacheService.setInCache(key, value, CacheTtl.SEARCH);

        // Then
        verify(valueOps).set(eq(key), eq(value), eq(Duration.ofMinutes(30)));
    }

    @Test
    void setInCache_usesCorrectTtlForCart() {
        // Given
        String key = CacheKeyConstants.cartKey(1L);
        String value = "cart-data";

        // When
        cacheService.setInCache(key, value, CacheTtl.CART);

        // Then
        verify(valueOps).set(eq(key), eq(value), eq(Duration.ofDays(7)));
    }

    @Test
    void setInCache_usesCorrectTtlForSession() {
        // Given
        String key = CacheKeyConstants.sessionKey(1L);
        String value = "session-data";

        // When
        cacheService.setInCache(key, value, CacheTtl.SESSION);

        // Then
        verify(valueOps).set(eq(key), eq(value), eq(Duration.ofHours(24)));
    }

    // =========================================================
    // Key pattern tests (Req 29.8-29.13)
    // =========================================================

    @Test
    void cacheKeyConstants_productKeyPattern() {
        assertThat(CacheKeyConstants.productKey(42L)).isEqualTo("product:42");
    }

    @Test
    void cacheKeyConstants_searchKeyPattern() {
        assertThat(CacheKeyConstants.searchKey("abc123")).isEqualTo("search:abc123");
    }

    @Test
    void cacheKeyConstants_sessionKeyPattern() {
        assertThat(CacheKeyConstants.sessionKey(7L)).isEqualTo("session:7");
    }

    @Test
    void cacheKeyConstants_cartKeyPattern() {
        assertThat(CacheKeyConstants.cartKey(5L)).isEqualTo("cart:5");
    }

    @Test
    void cacheKeyConstants_categoriesKey() {
        assertThat(CacheKeyConstants.CATEGORIES_KEY).isEqualTo("categories:all");
    }

    @Test
    void cacheKeyConstants_recommendationsKeyPattern() {
        assertThat(CacheKeyConstants.recommendationsKey(3L)).isEqualTo("recommendations:3");
    }
}
