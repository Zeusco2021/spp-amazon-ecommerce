package com.ecommerce.recommendation.service;

import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.RedisCacheService;
import com.ecommerce.recommendation.client.ProductServiceClient;
import com.ecommerce.recommendation.dto.ProductRecommendationResponse;
import com.ecommerce.recommendation.repository.UserActivityRepository;
import com.ecommerce.recommendation.repository.UserPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private UserActivityRepository userActivityRepository;
    @Mock private UserPreferenceRepository userPreferenceRepository;
    @Mock private CollaborativeFilteringService collaborativeFilteringService;
    @Mock private ProductServiceClient productServiceClient;
    @Mock private RedisCacheService cacheService;

    @InjectMocks
    private RecommendationService recommendationService;

    private ProductRecommendationResponse activeProduct(Long id, Long categoryId) {
        return ProductRecommendationResponse.builder()
                .id(id)
                .name("Product " + id)
                .categoryId(categoryId)
                .status("ACTIVE")
                .price(BigDecimal.TEN)
                .averageRating(4.0)
                .build();
    }

    // --- getPersonalizedRecommendations ---

    @Test
    void getPersonalizedRecommendations_cacheHit_returnsCachedResults() {
        String cacheKey = CacheKeyConstants.recommendationsKey(1L);
        List<ProductRecommendationResponse> cached = List.of(activeProduct(10L, 1L));
        when(cacheService.getFromCache(eq(cacheKey), eq(List.class))).thenReturn(cached);

        List<ProductRecommendationResponse> result =
                recommendationService.getPersonalizedRecommendations(1L, 10);

        assertEquals(1, result.size());
        verify(collaborativeFilteringService, never()).findSimilarUsers(any());
    }

    @Test
    void getPersonalizedRecommendations_cacheMiss_usesCollaborativeFiltering() {
        when(cacheService.getFromCache(anyString(), eq(List.class))).thenReturn(null);
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(Collections.emptyList());
        when(collaborativeFilteringService.findSimilarUsers(1L)).thenReturn(List.of(2L));
        when(collaborativeFilteringService.getProductsFromSimilarUsers(1L, List.of(2L))).thenReturn(List.of(20L));
        when(userPreferenceRepository.findCategoryIdsByUserId(1L)).thenReturn(Collections.emptyList());
        when(productServiceClient.getProduct(20L)).thenReturn(Optional.of(activeProduct(20L, 1L)));

        List<ProductRecommendationResponse> result =
                recommendationService.getPersonalizedRecommendations(1L, 10);

        assertFalse(result.isEmpty());
        assertEquals(20L, result.get(0).getId());
        verify(cacheService).setInCache(anyString(), any(), any());
    }

    @Test
    void getPersonalizedRecommendations_excludesAlreadyPurchased() {
        when(cacheService.getFromCache(anyString(), eq(List.class))).thenReturn(null);
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(20L));
        when(collaborativeFilteringService.findSimilarUsers(1L)).thenReturn(List.of(2L));
        when(collaborativeFilteringService.getProductsFromSimilarUsers(1L, List.of(2L))).thenReturn(List.of(20L, 30L));
        when(userPreferenceRepository.findCategoryIdsByUserId(1L)).thenReturn(Collections.emptyList());
        when(productServiceClient.getProduct(20L)).thenReturn(Optional.of(activeProduct(20L, 1L)));
        when(productServiceClient.getProduct(30L)).thenReturn(Optional.of(activeProduct(30L, 1L)));

        List<ProductRecommendationResponse> result =
                recommendationService.getPersonalizedRecommendations(1L, 10);

        // Product 20 was already purchased, should not appear
        assertTrue(result.stream().noneMatch(p -> p.getId().equals(20L)));
        assertTrue(result.stream().anyMatch(p -> p.getId().equals(30L)));
    }

    @Test
    void getPersonalizedRecommendations_filtersInactiveProducts() {
        when(cacheService.getFromCache(anyString(), eq(List.class))).thenReturn(null);
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(Collections.emptyList());
        when(collaborativeFilteringService.findSimilarUsers(1L)).thenReturn(List.of(2L));
        when(collaborativeFilteringService.getProductsFromSimilarUsers(1L, List.of(2L))).thenReturn(List.of(20L));
        when(userPreferenceRepository.findCategoryIdsByUserId(1L)).thenReturn(Collections.emptyList());

        ProductRecommendationResponse inactiveProduct = ProductRecommendationResponse.builder()
                .id(20L).name("Inactive").status("INACTIVE").categoryId(1L).build();
        when(productServiceClient.getProduct(20L)).thenReturn(Optional.of(inactiveProduct));

        List<ProductRecommendationResponse> result =
                recommendationService.getPersonalizedRecommendations(1L, 10);

        assertTrue(result.isEmpty());
    }

    // --- getSimilarProducts ---

    @Test
    void getSimilarProducts_returnsSameCategoryProducts() {
        when(productServiceClient.getProduct(10L)).thenReturn(Optional.of(activeProduct(10L, 5L)));
        when(productServiceClient.getProductsByCategory(5L, 11)).thenReturn(
                List.of(activeProduct(10L, 5L), activeProduct(11L, 5L), activeProduct(12L, 5L)));

        List<ProductRecommendationResponse> result =
                recommendationService.getSimilarProducts(10L, 10);

        // Should exclude the product itself
        assertTrue(result.stream().noneMatch(p -> p.getId().equals(10L)));
        assertTrue(result.stream().anyMatch(p -> p.getId().equals(11L)));
    }

    @Test
    void getSimilarProducts_productNotFound_returnsEmpty() {
        when(productServiceClient.getProduct(99L)).thenReturn(Optional.empty());

        List<ProductRecommendationResponse> result =
                recommendationService.getSimilarProducts(99L, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getSimilarProducts_noCategoryId_returnsEmpty() {
        ProductRecommendationResponse noCategoryProduct = ProductRecommendationResponse.builder()
                .id(10L).name("No Category").status("ACTIVE").categoryId(null).build();
        when(productServiceClient.getProduct(10L)).thenReturn(Optional.of(noCategoryProduct));

        List<ProductRecommendationResponse> result =
                recommendationService.getSimilarProducts(10L, 10);

        assertTrue(result.isEmpty());
    }

    // --- getTrendingProducts ---

    @Test
    void getTrendingProducts_returnsMostPurchasedProducts() {
        Object[] row1 = {100L, 50L};
        Object[] row2 = {200L, 30L};
        when(userActivityRepository.findTrendingProductIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(row1, row2));
        when(productServiceClient.getProduct(100L)).thenReturn(Optional.of(activeProduct(100L, 1L)));
        when(productServiceClient.getProduct(200L)).thenReturn(Optional.of(activeProduct(200L, 2L)));

        List<ProductRecommendationResponse> result = recommendationService.getTrendingProducts(10);

        assertEquals(2, result.size());
        assertEquals(100L, result.get(0).getId());
    }

    @Test
    void getTrendingProducts_noData_returnsEmpty() {
        when(userActivityRepository.findTrendingProductIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<ProductRecommendationResponse> result = recommendationService.getTrendingProducts(10);

        assertTrue(result.isEmpty());
    }

    // --- getFrequentlyBoughtTogether ---

    @Test
    void getFrequentlyBoughtTogether_returnsCopurchasedProducts() {
        Object[] row1 = {50L, 15L};
        when(userActivityRepository.findFrequentlyBoughtTogetherProductIds(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(row1));
        when(productServiceClient.getProduct(50L)).thenReturn(Optional.of(activeProduct(50L, 3L)));

        List<ProductRecommendationResponse> result =
                recommendationService.getFrequentlyBoughtTogether(10L, 10);

        assertEquals(1, result.size());
        assertEquals(50L, result.get(0).getId());
    }

    @Test
    void getFrequentlyBoughtTogether_noData_returnsEmpty() {
        when(userActivityRepository.findFrequentlyBoughtTogetherProductIds(eq(10L), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<ProductRecommendationResponse> result =
                recommendationService.getFrequentlyBoughtTogether(10L, 10);

        assertTrue(result.isEmpty());
    }
}
