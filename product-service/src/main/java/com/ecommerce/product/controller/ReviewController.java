package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ReviewRequest;
import com.ecommerce.product.dto.ReviewResponse;
import com.ecommerce.product.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for product reviews and ratings.
 * Requirements: 4.1, 4.2, 4.3, 4.5
 */
@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * POST /api/products/{productId}/reviews - Add a review to a product.
     * Returns 201 Created with the created review.
     */
    @PostMapping
    public ResponseEntity<ReviewResponse> addReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse response = reviewService.addReview(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/products/{productId}/reviews - Get paginated reviews for a product.
     * Returns 200 OK with a page of reviews ordered by date descending.
     */
    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReviewResponse> reviews = reviewService.getReviews(productId, page, size);
        return ResponseEntity.ok(reviews);
    }
}
