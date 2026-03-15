package com.ecommerce.product.service;

import com.ecommerce.product.dto.ReviewRequest;
import com.ecommerce.product.dto.ReviewResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.Review;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for product reviews and ratings.
 * Requirements: 4.1, 4.2, 4.3, 4.5
 */
@Service
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public ReviewService(ReviewRepository reviewRepository, ProductRepository productRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    /**
     * Add a review to a product.
     * Recalculates average rating and increments review count.
     * Requirements: 4.1, 4.2, 4.3
     */
    public ReviewResponse addReview(Long productId, ReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Review review = Review.builder()
                .productId(productId)
                .userId(request.getUserId())
                .rating(request.getRating())
                .title(request.getTitle())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);

        // Recalculate average rating after saving the new review
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        product.setAverageRating(avg != null ? avg : 0.0);
        product.setReviewCount(product.getReviewCount() + 1);
        productRepository.save(product);

        return toReviewResponse(saved);
    }

    /**
     * Get paginated reviews for a product, ordered by creation date descending.
     * Requirements: 4.5
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviews(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reviewRepository.findByProductId(productId, pageable)
                .map(this::toReviewResponse);
    }

    // --- Private helpers ---

    private ReviewResponse toReviewResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
