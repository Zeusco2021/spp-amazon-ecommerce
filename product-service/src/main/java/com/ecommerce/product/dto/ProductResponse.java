package com.ecommerce.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product response DTO used for API responses and cache storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private String sku;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Integer stockQuantity;
    private Long categoryId;
    private String categoryName;
    private Long sellerId;
    private String brand;
    private List<String> imageUrls;
    private String status;
    private Double averageRating;
    private Integer reviewCount;
    private Map<String, String> specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
