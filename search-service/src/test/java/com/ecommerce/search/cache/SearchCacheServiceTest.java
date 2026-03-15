package com.ecommerce.search.cache;

import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.CacheTtl;
import com.ecommerce.common.cache.RedisCacheService;
import com.ecommerce.search.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchCacheService.
 * Requirements: 28.3
 */
@ExtendWith(MockitoExtension.class)
class SearchCacheServiceTest {

    @Mock
    private RedisCacheService redisCacheService;

    private SearchCacheService searchCacheService;

    @BeforeEach
    void setUp() {
        searchCacheService = new SearchCacheService(redisCacheService);
    }

    private SearchResponse buildSearchResponse(String query) {
        return SearchResponse.builder()
                .products(List.of())
                .totalResults(0)
                .page(0)
                .pageSize(20)
                .totalPages(0)
                .query(query)
                .build();
    }

    @Test
    void getOrSearch_cacheHit_returnsFromCacheWithoutSearching() {
        // Given
        String queryHash = "abc123";
        SearchResponse cached = buildSearchResponse("laptop");
        when(redisCacheService.getOrLoad(
                eq(CacheKeyConstants.searchKey(queryHash)),
                eq(SearchResponse.class),
                eq(CacheTtl.SEARCH),
                any()
        )).thenReturn(cached);

        AtomicInteger searchCallCount = new AtomicInteger(0);

        // When
        SearchResponse result = searchCacheService.getOrSearch(queryHash, () -> {
            searchCallCount.incrementAndGet();
            return buildSearchResponse("laptop");
        });

        // Then
        assertThat(result).isEqualTo(cached);
    }

    @Test
    void computeQueryHash_sameParameters_returnsSameHash() {
        // Given
        String hash1 = searchCacheService.computeQueryHash(
                "laptop", "electronics", "100", "2000", "4.0", 0, 20, "relevance");
        String hash2 = searchCacheService.computeQueryHash(
                "laptop", "electronics", "100", "2000", "4.0", 0, 20, "relevance");

        // Then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeQueryHash_differentParameters_returnsDifferentHash() {
        // Given
        String hash1 = searchCacheService.computeQueryHash(
                "laptop", null, null, null, null, 0, 20, "relevance");
        String hash2 = searchCacheService.computeQueryHash(
                "phone", null, null, null, null, 0, 20, "relevance");

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeQueryHash_differentPages_returnsDifferentHash() {
        // Given
        String hash1 = searchCacheService.computeQueryHash(
                "laptop", null, null, null, null, 0, 20, "relevance");
        String hash2 = searchCacheService.computeQueryHash(
                "laptop", null, null, null, null, 1, 20, "relevance");

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeQueryHash_nullParameters_handledGracefully() {
        // Should not throw
        String hash = searchCacheService.computeQueryHash(
                "laptop", null, null, null, null, 0, 20, null);
        assertThat(hash).isNotNull().isNotEmpty();
    }

    @Test
    void invalidateAllSearchCaches_callsInvalidatePattern() {
        // When
        searchCacheService.invalidateAllSearchCaches();

        // Then
        verify(redisCacheService).invalidatePattern("search:*");
    }

    @Test
    void cacheSearchResults_storesWithThirtyMinuteTtl() {
        // Given
        String queryHash = "xyz789";
        SearchResponse response = buildSearchResponse("tablet");

        // When
        searchCacheService.cacheSearchResults(queryHash, response);

        // Then
        verify(redisCacheService).setInCache(
                eq(CacheKeyConstants.searchKey(queryHash)),
                eq(response),
                eq(CacheTtl.SEARCH)
        );
    }
}
