package com.ecommerce.search.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Search response DTO used for API responses and Redis cache storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponse {

    private List<SearchProductResult> products;
    private long totalResults;
    private int page;
    private int pageSize;
    private int totalPages;
    private String query;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchProductResult {
        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private BigDecimal discountPrice;
        private String brand;
        private String categoryName;
        private String imageUrl;
        private Double averageRating;
        private Integer reviewCount;
        private String status;
        private Double relevanceScore;
    }
}
