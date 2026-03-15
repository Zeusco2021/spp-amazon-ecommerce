package com.ecommerce.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.ecommerce.search.cache.SearchCacheService;
import com.ecommerce.search.document.ProductDocument;
import com.ecommerce.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for full-text product search with filters using Elasticsearch.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchCacheService searchCacheService;

    /**
     * Search products with full-text query and optional filters.
     * - Multi-match on name, description, brand with fuzzy matching (Req 5.1, 5.2)
     * - Filter by categoryId (Req 5.3)
     * - Filter by price range (Req 5.4)
     * - Filter by minimum rating (Req 5.5)
     * - Only ACTIVE products (Req 5.6)
     * - Sorting by relevance, price_asc, price_desc, rating (Req 5.8)
     */
    public SearchResponse search(String query, Long categoryId, Double minPrice, Double maxPrice,
                                 Double minRating, String sortBy, int page, int size) {

        String queryHash = searchCacheService.computeQueryHash(
                query,
                categoryId != null ? categoryId.toString() : null,
                minPrice != null ? minPrice.toString() : null,
                maxPrice != null ? maxPrice.toString() : null,
                minRating != null ? minRating.toString() : null,
                page, size, sortBy
        );

        return searchCacheService.getOrSearch(queryHash, () -> executeSearch(query, categoryId, minPrice, maxPrice, minRating, sortBy, page, size));
    }

    private SearchResponse executeSearch(String query, Long categoryId, Double minPrice, Double maxPrice,
                                         Double minRating, String sortBy, int page, int size) {
        List<Query> filters = buildFilters(categoryId, minPrice, maxPrice, minRating);

        // Multi-match query with fuzzy matching on name, description, brand
        Query multiMatchQuery = Query.of(q -> q
                .multiMatch(mm -> mm
                        .query(query)
                        .fields("name^3", "brand^2", "description")
                        .fuzziness("AUTO")
                        .type(TextQueryType.BestFields)
                )
        );

        // Combine multi-match with filters using bool query
        Query boolQuery = Query.of(q -> q
                .bool(b -> {
                    b.must(multiMatchQuery);
                    filters.forEach(b::filter);
                    return b;
                })
        );

        NativeQuery nativeQuery = buildNativeQuery(boolQuery, sortBy, page, size);

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        return mapToSearchResponse(hits, query, page, size);
    }

    private List<Query> buildFilters(Long categoryId, Double minPrice, Double maxPrice, Double minRating) {
        List<Query> filters = new ArrayList<>();

        // Always filter by ACTIVE status (Req 5.6)
        filters.add(Query.of(q -> q
                .term(t -> t
                        .field("status")
                        .value(FieldValue.of("ACTIVE"))
                )
        ));

        // Filter by category (Req 5.3)
        if (categoryId != null) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("categoryId")
                            .value(FieldValue.of(categoryId))
                    )
            ));
        }

        // Filter by price range (Req 5.4)
        if (minPrice != null || maxPrice != null) {
            filters.add(Query.of(q -> q
                    .range(r -> {
                        r.field("price");
                        if (minPrice != null) r.gte(co.elastic.clients.json.JsonData.of(minPrice));
                        if (maxPrice != null) r.lte(co.elastic.clients.json.JsonData.of(maxPrice));
                        return r;
                    })
            ));
        }

        // Filter by minimum rating (Req 5.5)
        if (minRating != null) {
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("averageRating")
                            .gte(co.elastic.clients.json.JsonData.of(minRating))
                    )
            ));
        }

        return filters;
    }

    private NativeQuery buildNativeQuery(Query boolQuery, String sortBy, int page, int size) {
        NativeQuery.Builder builder = new NativeQuery.Builder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(page, size));

        // Sorting (Req 5.8)
        if (sortBy != null) {
            switch (sortBy) {
                case "price_asc" -> builder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
                case "price_desc" -> builder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
                case "rating" -> builder.withSort(s -> s.field(f -> f.field("averageRating").order(SortOrder.Desc)));
                // "relevance" is the default (score-based), no explicit sort needed
            }
        }

        return builder.build();
    }

    private SearchResponse mapToSearchResponse(SearchHits<ProductDocument> hits, String query, int page, int size) {
        List<SearchResponse.SearchProductResult> results = hits.getSearchHits().stream()
                .map(this::mapHitToResult)
                .toList();

        long totalHits = hits.getTotalHits();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalHits / size) : 0;

        return SearchResponse.builder()
                .products(results)
                .totalResults(totalHits)
                .page(page)
                .pageSize(size)
                .totalPages(totalPages)
                .query(query)
                .build();
    }

    /**
     * Return product name suggestions based on a prefix using match_phrase_prefix.
     * Only ACTIVE products are considered.
     * Requirements: 5.7
     */
    public List<String> autocomplete(String prefix, int size) {
        int effectiveSize = Math.min(size, 20);

        Query activeFilter = Query.of(q -> q
                .term(t -> t.field("status").value(FieldValue.of("ACTIVE")))
        );

        Query matchPhrasePrefixQuery = Query.of(q -> q
                .matchPhrasePrefix(mp -> mp.field("name").query(prefix))
        );

        Query boolQuery = Query.of(q -> q
                .bool(b -> b.must(matchPhrasePrefixQuery).filter(activeFilter))
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(0, effectiveSize))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Return search suggestions based on a query string using multi-match.
     * Only ACTIVE products are considered.
     * Requirements: 5.7
     */
    public List<String> suggestions(String query, int size) {
        int effectiveSize = Math.min(size, 20);

        Query activeFilter = Query.of(q -> q
                .term(t -> t.field("status").value(FieldValue.of("ACTIVE")))
        );

        Query multiMatchQuery = Query.of(q -> q
                .multiMatch(mm -> mm
                        .query(query)
                        .fields("name^3", "brand^2", "description")
                        .fuzziness("AUTO")
                        .type(TextQueryType.BestFields)
                )
        );

        Query boolQuery = Query.of(q -> q
                .bool(b -> b.must(multiMatchQuery).filter(activeFilter))
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(0, effectiveSize))
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private SearchResponse.SearchProductResult mapHitToResult(SearchHit<ProductDocument> hit) {
        ProductDocument doc = hit.getContent();
        return SearchResponse.SearchProductResult.builder()
                .id(doc.getId() != null ? Long.parseLong(doc.getId()) : null)
                .name(doc.getName())
                .description(doc.getDescription())
                .price(doc.getPrice() != null ? BigDecimal.valueOf(doc.getPrice()) : null)
                .discountPrice(doc.getDiscountPrice() != null ? BigDecimal.valueOf(doc.getDiscountPrice()) : null)
                .brand(doc.getBrand())
                .imageUrl(doc.getImageUrls() != null && !doc.getImageUrls().isEmpty() ? doc.getImageUrls().get(0) : null)
                .averageRating(doc.getAverageRating())
                .reviewCount(doc.getReviewCount())
                .status(doc.getStatus())
                .relevanceScore(hit.getScore() != 0.0f ? (double) hit.getScore() : null)
                .build();
    }
}
