package com.ecommerce.search.cache;

import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.CacheTtl;
import com.ecommerce.common.cache.RedisCacheService;
import com.ecommerce.search.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Search-specific cache service.
 * Caches search results with query hash as key.
 * Requirements: 28.3
 */
@Service
public class SearchCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SearchCacheService.class);

    private final RedisCacheService cacheService;

    public SearchCacheService(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Get search results from cache or execute search and cache the result.
     * Key pattern: search:{queryHash}
     * TTL: 30 minutes (auto-invalidation by TTL)
     * Requirements: 28.3
     */
    public SearchResponse getOrSearch(String queryHash, Supplier<SearchResponse> searcher) {
        String key = CacheKeyConstants.searchKey(queryHash);
        return cacheService.getOrLoad(key, SearchResponse.class, CacheTtl.SEARCH, searcher);
    }

    /**
     * Get search results from cache only.
     */
    public SearchResponse getFromCache(String queryHash) {
        String key = CacheKeyConstants.searchKey(queryHash);
        return cacheService.getFromCache(key, SearchResponse.class);
    }

    /**
     * Store search results in cache with 30-minute TTL.
     */
    public void cacheSearchResults(String queryHash, SearchResponse response) {
        String key = CacheKeyConstants.searchKey(queryHash);
        cacheService.setInCache(key, response, CacheTtl.SEARCH);
        logger.debug("Cached search results for hash: {}", queryHash);
    }

    /**
     * Compute a stable hash for a search query including all parameters.
     * Used as the cache key suffix.
     * Requirements: 28.3
     *
     * @param query      search query string
     * @param categories comma-separated category filters
     * @param minPrice   minimum price filter
     * @param maxPrice   maximum price filter
     * @param minRating  minimum rating filter
     * @param page       page number
     * @param size       page size
     * @param sortBy     sort field
     * @return MD5 hex hash of the combined parameters
     */
    public String computeQueryHash(String query, String categories, String minPrice,
                                   String maxPrice, String minRating, int page,
                                   int size, String sortBy) {
        String combined = String.join("|",
                nullSafe(query),
                nullSafe(categories),
                nullSafe(minPrice),
                nullSafe(maxPrice),
                nullSafe(minRating),
                String.valueOf(page),
                String.valueOf(size),
                nullSafe(sortBy)
        );
        return DigestUtils.md5DigestAsHex(combined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Invalidate all search caches (e.g., when products are updated).
     * Requirements: 33.2
     */
    public void invalidateAllSearchCaches() {
        cacheService.invalidatePattern(CacheKeyConstants.SEARCH_KEY_PREFIX + "*");
        logger.debug("Invalidated all search caches");
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
