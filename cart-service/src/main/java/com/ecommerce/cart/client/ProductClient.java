package com.ecommerce.cart.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * HTTP client for communicating with Product Service.
 * Used to validate product existence and availability.
 * Requirements: 6.1
 */
@Component
public class ProductClient {

    private static final Logger logger = LoggerFactory.getLogger(ProductClient.class);

    private final RestTemplate restTemplate;
    private final String productServiceUrl;

    public ProductClient(RestTemplate restTemplate,
                         @Value("${services.product-service.url:http://product-service:8082}") String productServiceUrl) {
        this.restTemplate = restTemplate;
        this.productServiceUrl = productServiceUrl;
    }

    /**
     * Fetch product details from Product Service.
     * Returns null if product not found or service unavailable.
     * Requirements: 6.1
     */
    public ProductInfo getProduct(Long productId) {
        try {
            String url = productServiceUrl + "/api/products/" + productId;
            return restTemplate.getForObject(url, ProductInfo.class);
        } catch (Exception e) {
            logger.error("Failed to fetch product {} from product-service: {}", productId, e.getMessage());
            return null;
        }
    }

    /**
     * Minimal product info needed by Cart Service.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductInfo {
        private Long id;
        private String name;
        private String sku;
        private String imageUrl;
        private BigDecimal price;
        private BigDecimal discountPrice;
        private String status;
        private Integer stockQuantity;

        /** Returns the effective price (discountPrice if set, otherwise price). */
        public BigDecimal getEffectivePrice() {
            return (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0)
                    ? discountPrice : price;
        }

        /** Returns true if product is ACTIVE and has stock. */
        public boolean isAvailable() {
            return "ACTIVE".equals(status) && stockQuantity != null && stockQuantity > 0;
        }
    }
}
