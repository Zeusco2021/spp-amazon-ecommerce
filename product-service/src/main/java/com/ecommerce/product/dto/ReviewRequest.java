package com.ecommerce.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a product review.
 * Requirements: 4.1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @NotNull
    private Long userId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @NotBlank(message = "Review title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Review comment is required")
    @Size(max = 2000, message = "Comment must not exceed 2000 characters")
    private String comment;
}
