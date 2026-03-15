package com.ecommerce.recommendation.service;

import com.ecommerce.common.cache.CacheKeyConstants;
import com.ecommerce.common.cache.CacheTtl;
import com.ecommerce.common.cache.RedisCacheService;
import com.ecommerce.recommendation.client.ProductServiceClient;
import com.ecommerce.recommendation.dto.ProductRecommendationResponse;
import com.ecommerce.recommendation.repository.UserActivityRepository;
import com.ecommerce.recommendation.repository.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommendation service providing personalized product recommendations.
 * Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int TRENDING_DAYS = 30;

    private final UserActivityRepository userActivityRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final CollaborativeFilteringService collaborativeFilteringService;
    private final ProductServiceClient productServiceClient;
    private final RedisCacheService cacheService;

    public RecommendationService(UserActivityRepository userActivityRepository,
                                 UserPreferenceRepository userPreferenceRepository,
                                 CollaborativeFilteringService collaborativeFilteringService,
                                 ProductServiceClient productServiceClient,
                                 RedisCacheService cacheService) {
        this.userActivityRepository = userActivityRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.productServiceClient = productServiceClient;
        this.cacheService = cacheService;
    }

    /**
     * Get personalized recommendations for a user.
     * 1. Find similar users via Jaccard collaborative filtering
     * 2. Get products from similar users (exclude already purchased)
     * 3. Fill remaining slots with popular products from preferred categories
     * Requirements: 11.1, 11.2, 11.3
     */
    @SuppressWarnings("unchecked")
    public List<ProductRecommendationResponse> getPersonalizedRecommendations(Long userId, int limit) {
        String cacheKey = CacheKeyConstants.recommendationsKey(userId);

        List<ProductRecommendationResponse> cached = cacheService.getFromCache(cacheKey, List.class);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().limit(limit).collect(Collectors.toList());
        }

        Set<Long> alreadyPurchased = new HashSet<>(userActivityRepository.findPurchasedProductIdsByUserId(userId));
        Set<Long> recommendedProductIds = new LinkedHashSet<>();

        // Step 1: Collaborative filtering — products from similar users
        List<Long> similarUsers = collaborativeFilteringService.findSimilarUsers(userId);
        List<Long> cfProducts = collaborativeFilteringService.getProductsFromSimilarUsers(userId, similarUsers);
        recommendedProductIds.addAll(cfProducts);

        // Step 2: Fill with popular products from preferred categories
        if (recommendedProductIds.size() < limit) {
            List<Long> preferredCategories = userPreferenceRepository.findCategoryIdsByUserId(userId);
            for (Long categoryId : preferredCategories) {
                if (recommendedProductIds.size() >= limit * 2) break;
                List<ProductRecommendationResponse> categoryProducts =
                        productServiceClient.getProductsByCategory(categoryId, limit);
                categoryProducts.stream()
                        .filter(p -> !alreadyPurchased.contains(p.getId()))
                        .map(ProductRecommendationResponse::getId)
                        .forEach(recommendedProductIds::add);
            }
        }

        // Fetch product details and filter active products
        List<ProductRecommendationResponse> recommendations = recommendedProductIds.stream()
                .limit(limit * 2L)
                .map(productServiceClient::getProduct)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .filter(p -> !alreadyPurchased.contains(p.getId()))
                .limit(limit)
                .collect(Collectors.toList());

        if (!recommendations.isEmpty()) {
            cacheService.setInCache(cacheKey, recommendations, CacheTtl.RECOMMENDATIONS);
        }

        logger.debug("Generated {} recommendations for userId={}", recommendations.size(), userId);
        return recommendations;
    }

    /**
     * Get products similar to a given product (same category).
     * Requirements: 11.4
     */
    public List<ProductRecommendationResponse> getSimilarProducts(Long productId, int limit) {
        Optional<ProductRecommendationResponse> product = productServiceClient.getProduct(productId);
        if (product.isEmpty()) {
            return Collections.emptyList();
        }

        Long categoryId = product.get().getCategoryId();
        if (categoryId == null) {
            return Collections.emptyList();
        }

        return productServiceClient.getProductsByCategory(categoryId, limit + 1).stream()
                .filter(p -> !p.getId().equals(productId))
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get trending products based on recent purchase count.
     * Requirements: 11.6
     */
    public List<ProductRecommendationResponse> getTrendingProducts(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(TRENDING_DAYS);
        List<Object[]> trendingRows = userActivityRepository.findTrendingProductIds(
                since, PageRequest.of(0, limit * 2));

        return trendingRows.stream()
                .map(row -> (Long) row[0])
                .map(productServiceClient::getProduct)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get products frequently bought together with a given product.
     * Requirements: 11.5
     */
    public List<ProductRecommendationResponse> getFrequentlyBoughtTogether(Long productId, int limit) {
        List<Object[]> rows = userActivityRepository.findFrequentlyBoughtTogetherProductIds(
                productId, PageRequest.of(0, limit * 2));

        return rows.stream()
                .map(row -> (Long) row[0])
                .map(productServiceClient::getProduct)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
