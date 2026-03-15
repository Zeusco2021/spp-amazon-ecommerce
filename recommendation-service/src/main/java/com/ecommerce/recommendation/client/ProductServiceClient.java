package com.ecommerce.recommendation.client;

import com.ecommerce.recommendation.dto.ProductRecommendationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * HTTP client for fetching product details from Product Service.
 */
@Component
public class ProductServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(ProductServiceClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.product-service.url:http://localhost:8082}")
    private String productServiceUrl;

    public ProductServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<ProductRecommendationResponse> getProduct(Long productId) {
        try {
            ProductRecommendationResponse response = restTemplate.getForObject(
                    productServiceUrl + "/api/products/" + productId,
                    ProductRecommendationResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            logger.warn("Failed to fetch product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<ProductRecommendationResponse> getProductsByCategory(Long categoryId, int limit) {
        try {
            ProductRecommendationResponse[] products = restTemplate.getForObject(
                    productServiceUrl + "/api/products?categoryId=" + categoryId + "&size=" + limit + "&sortBy=averageRating",
                    ProductRecommendationResponse[].class);
            return products != null ? Arrays.asList(products) : Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Failed to fetch products for category {}: {}", categoryId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
