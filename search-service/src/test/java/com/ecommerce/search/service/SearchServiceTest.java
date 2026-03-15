package com.ecommerce.search.service;

import com.ecommerce.search.cache.SearchCacheService;
import com.ecommerce.search.document.ProductDocument;
import com.ecommerce.search.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchService.
 * Validates that search(), autocomplete(), and suggestions() delegate correctly.
 * Requirements: 5.1, 5.7
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SearchCacheService searchCacheService;

    @InjectMocks
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        // Default: computeQueryHash returns a fixed hash
        when(searchCacheService.computeQueryHash(any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any()))
                .thenReturn("fixed-hash-abc123");
    }

    @Test
    @DisplayName("search() calls searchCacheService.getOrSearch() with the computed hash")
    void search_callsCacheService_withComputedHash() {
        SearchResponse expected = SearchResponse.builder()
                .products(List.of())
                .totalResults(0)
                .page(0)
                .pageSize(10)
                .totalPages(0)
                .query("laptop")
                .build();

        when(searchCacheService.getOrSearch(eq("fixed-hash-abc123"), any()))
                .thenReturn(expected);

        SearchResponse result = searchService.search("laptop", null, null, null, null, null, 0, 10);

        assertThat(result).isEqualTo(expected);
        verify(searchCacheService).computeQueryHash(eq("laptop"), isNull(), isNull(), isNull(),
                isNull(), eq(0), eq(10), isNull());
        verify(searchCacheService).getOrSearch(eq("fixed-hash-abc123"), any(Supplier.class));
    }

    @Test
    @DisplayName("autocomplete() calls elasticsearchOperations.search() and returns product names")
    @SuppressWarnings("unchecked")
    void autocomplete_callsElasticsearchOperations() {
        ProductDocument doc = ProductDocument.builder().id("1").name("Laptop Pro").status("ACTIVE").build();
        SearchHit<ProductDocument> hit = mock(SearchHit.class);
        SearchHits<ProductDocument> hits = mock(SearchHits.class);

        when(hit.getContent()).thenReturn(doc);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class)))
                .thenReturn(hits);

        List<String> result = searchService.autocomplete("Lap", 5);

        assertThat(result).containsExactly("Laptop Pro");
        verify(elasticsearchOperations).search(any(Query.class), eq(ProductDocument.class));
    }

    @Test
    @DisplayName("suggestions() calls elasticsearchOperations.search() and returns product names")
    @SuppressWarnings("unchecked")
    void suggestions_callsElasticsearchOperations() {
        ProductDocument doc = ProductDocument.builder().id("2").name("Gaming Mouse").status("ACTIVE").build();
        SearchHit<ProductDocument> hit = mock(SearchHit.class);
        SearchHits<ProductDocument> hits = mock(SearchHits.class);

        when(hit.getContent()).thenReturn(doc);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class)))
                .thenReturn(hits);

        List<String> result = searchService.suggestions("gaming", 5);

        assertThat(result).containsExactly("Gaming Mouse");
        verify(elasticsearchOperations).search(any(Query.class), eq(ProductDocument.class));
    }

    @Test
    @DisplayName("autocomplete() caps size at 20 even when larger value is requested")
    @SuppressWarnings("unchecked")
    void autocomplete_capsResultsAt20() {
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class)))
                .thenReturn(hits);

        // Should not throw; internally caps at 20
        List<String> result = searchService.autocomplete("test", 100);

        assertThat(result).isEmpty();
        verify(elasticsearchOperations).search(any(Query.class), eq(ProductDocument.class));
    }

    @Test
    @DisplayName("search() passes sortBy parameter through to cache hash computation")
    void search_passesSortByToHashComputation() {
        SearchResponse expected = SearchResponse.builder().products(List.of()).totalResults(0)
                .page(0).pageSize(10).totalPages(0).query("phone").build();
        when(searchCacheService.getOrSearch(any(), any())).thenReturn(expected);

        searchService.search("phone", null, null, null, null, "price_asc", 0, 10);

        verify(searchCacheService).computeQueryHash(eq("phone"), isNull(), isNull(), isNull(),
                isNull(), eq(0), eq(10), eq("price_asc"));
    }
}
