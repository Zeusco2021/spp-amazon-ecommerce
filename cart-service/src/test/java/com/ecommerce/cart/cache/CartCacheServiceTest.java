package com.ecommerce.cart.cache;

import com.ecommerce.cart.dto.CartResponse;
import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.CacheTtl;
import com.ecommerce.common.cache.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CartCacheService.
 * Requirements: 6.6, 6.7, 17.4, 28.2
 */
@ExtendWith(MockitoExtension.class)
class CartCacheServiceTest {

    @Mock
    private RedisCacheService redisCacheService;

    private CartCacheService cartCacheService;

    @BeforeEach
    void setUp() {
        cartCacheService = new CartCacheService(redisCacheService);
    }

    private CartResponse buildCart(Long userId) {
        return CartResponse.builder()
                .id(1L)
                .userId(userId)
                .items(List.of())
                .subtotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();
    }

    @Test
    void getOrLoad_cacheHit_returnsFromRedisWithoutCallingMysql() {
        // Given
        Long userId = 1L;
        CartResponse cachedCart = buildCart(userId);
        when(redisCacheService.getFromCache(CacheKeyConstants.cartKey(userId), CartResponse.class))
                .thenReturn(cachedCart);

        AtomicInteger mysqlCallCount = new AtomicInteger(0);

        // When
        CartResponse result = cartCacheService.getOrLoad(userId, () -> {
            mysqlCallCount.incrementAndGet();
            return buildCart(userId);
        });

        // Then
        assertThat(result).isEqualTo(cachedCart);
        assertThat(mysqlCallCount.get()).isZero(); // MySQL not called
    }

    @Test
    void getOrLoad_cacheMiss_loadsFromMysqlAndCachesResult() {
        // Given
        Long userId = 2L;
        CartResponse dbCart = buildCart(userId);
        when(redisCacheService.getFromCache(CacheKeyConstants.cartKey(userId), CartResponse.class))
                .thenReturn(null);

        // When
        CartResponse result = cartCacheService.getOrLoad(userId, () -> dbCart);

        // Then
        assertThat(result).isEqualTo(dbCart);
        verify(redisCacheService).setInCache(
                eq(CacheKeyConstants.cartKey(userId)),
                eq(dbCart),
                eq(CacheTtl.CART)
        );
    }

    @Test
    void getOrLoad_redisFails_fallsBackToMysql() {
        // Given
        Long userId = 3L;
        CartResponse dbCart = buildCart(userId);
        when(redisCacheService.getFromCache(CacheKeyConstants.cartKey(userId), CartResponse.class))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // When
        CartResponse result = cartCacheService.getOrLoad(userId, () -> dbCart);

        // Then - should return MySQL data even when Redis fails
        assertThat(result).isEqualTo(dbCart);
    }

    @Test
    void cacheCart_storesWithSevenDayTtl() {
        // Given
        CartResponse cart = buildCart(10L);

        // When
        cartCacheService.cacheCart(cart);

        // Then
        verify(redisCacheService).setInCache(
                eq(CacheKeyConstants.cartKey(10L)),
                eq(cart),
                eq(CacheTtl.CART)
        );
    }

    @Test
    void invalidateCart_deletesCartKey() {
        // Given
        Long userId = 5L;

        // When
        cartCacheService.invalidateCart(userId);

        // Then
        verify(redisCacheService).invalidate(CacheKeyConstants.cartKey(userId));
    }
}
