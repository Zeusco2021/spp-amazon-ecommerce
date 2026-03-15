package com.ecommerce.recommendation.controller;

import com.ecommerce.common.dto.ApiResponse;
import com.ecommerce.recommendation.dto.ProductRecommendationResponse;
import com.ecommerce.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for recommendation endpoints.
 * Requirements: 11.1-11.6
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * GET /api/recommendations/user/{userId}
     * Returns personalized recommendations for a user.
     * Requirements: 11.1, 11.2, 11.3
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ProductRecommendationResponse>>> getPersonalizedRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductRecommendationResponse> recommendations =
                recommendationService.getPersonalizedRecommendations(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }

    /**
     * GET /api/recommendations/product/{productId}/similar
     * Returns products similar to the given product (same category).
     * Requirements: 11.4
     */
    @GetMapping("/product/{productId}/similar")
    public ResponseEntity<ApiResponse<List<ProductRecommendationResponse>>> getSimilarProducts(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductRecommendationResponse> similar =
                recommendationService.getSimilarProducts(productId, limit);
        return ResponseEntity.ok(ApiResponse.success(similar));
    }

    /**
     * GET /api/recommendations/trending
     * Returns trending products based on recent purchase volume.
     * Requirements: 11.6
     */
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<ProductRecommendationResponse>>> getTrendingProducts(
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductRecommendationResponse> trending =
                recommendationService.getTrendingProducts(limit);
        return ResponseEntity.ok(ApiResponse.success(trending));
    }

    /**
     * GET /api/recommendations/frequently-bought-together/{productId}
     * Returns products frequently bought together with the given product.
     * Requirements: 11.5
     */
    @GetMapping("/frequently-bought-together/{productId}")
    public ResponseEntity<ApiResponse<List<ProductRecommendationResponse>>> getFrequentlyBoughtTogether(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductRecommendationResponse> fbt =
                recommendationService.getFrequentlyBoughtTogether(productId, limit);
        return ResponseEntity.ok(ApiResponse.success(fbt));
    }
}
