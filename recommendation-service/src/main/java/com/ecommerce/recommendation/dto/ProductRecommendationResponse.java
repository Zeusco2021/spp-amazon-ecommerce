package com.ecommerce.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;

/**
 * Lightweight product DTO for recommendation responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRecommendationResponse {
    private Long id;
    private String name;
    private String brand;
    private BigDecimal price;
    private BigDecimal discountPrice;
    private Long categoryId;
    private String status;
    private Double averageRating;
    private Integer reviewCount;
}
