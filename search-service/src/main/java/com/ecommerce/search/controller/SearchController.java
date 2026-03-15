package com.ecommerce.search.controller;

import com.ecommerce.search.dto.SearchResponse;
import com.ecommerce.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for product search.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Full-text search with optional filters.
     *
     * @param query      required search query string
     * @param categoryId optional category filter
     * @param minPrice   optional minimum price filter
     * @param maxPrice   optional maximum price filter
     * @param minRating  optional minimum average rating filter
     * @param sortBy     sort order: relevance (default), price_asc, price_desc, rating
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false, defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Cap page size at 100 (Req 17.7)
        int effectiveSize = Math.min(size, 100);

        SearchResponse response = searchService.search(q, categoryId, minPrice, maxPrice, minRating, sortBy, page, effectiveSize);
        return ResponseEntity.ok(response);
    }

    /**
     * Autocomplete endpoint: returns product name suggestions based on a prefix.
     * Requirements: 5.7
     *
     * @param q    prefix string to match against product names
     * @param size maximum number of suggestions (default 10, max 20)
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<String> suggestions = searchService.autocomplete(q, size);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Suggestions endpoint: returns search term suggestions based on a query.
     * Requirements: 5.7
     *
     * @param q    query string
     * @param size maximum number of suggestions (default 10, max 20)
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> suggestions(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<String> results = searchService.suggestions(q, size);
        return ResponseEntity.ok(results);
    }
}
