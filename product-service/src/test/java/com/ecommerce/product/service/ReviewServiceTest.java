package com.ecommerce.product.service;

import com.ecommerce.product.dto.ReviewRequest;
import com.ecommerce.product.dto.ReviewResponse;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.Review;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReviewService.
 * Requirements: 4, 17
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ReviewService reviewService;

    private Product product;
    private ReviewRequest reviewRequest;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("Test Product")
                .sku("SKU-001")
                .reviewCount(0)
                .averageRating(0.0)
                .build();

        reviewRequest = new ReviewRequest(42L, 4, "Great product!", "Great product!");
    }

    // --- addReview tests ---

    @Test
    void addReview_success_recalculatesRating() {
        // Requirements: 4.2, 4.3
        Review savedReview = Review.builder()
                .id(1L)
                .productId(1L)
                .userId(42L)
                .rating(4)
                .comment("Great product!")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);
        when(reviewRepository.findAverageRatingByProductId(1L)).thenReturn(4.5);
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ReviewResponse response = reviewService.addReview(1L, reviewRequest);

        assertThat(product.getReviewCount()).isEqualTo(1);
        assertThat(product.getAverageRating()).isEqualTo(4.5);
        assertThat(response.getRating()).isEqualTo(4);
        verify(productRepository).save(product);
    }

    @Test
    void addReview_productNotFound_throwsException() {
        // Requirement 4.1
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.addReview(99L, reviewRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Product not found");

        verify(reviewRepository, never()).save(any());
    }

    // --- getReviews tests ---

    @Test
    void getReviews_returnsPaginatedReviews() {
        // Requirement 4.5
        Review r1 = Review.builder().id(1L).productId(1L).userId(1L).rating(5).build();
        Review r2 = Review.builder().id(2L).productId(1L).userId(2L).rating(3).build();
        Page<Review> reviewPage = new PageImpl<>(List.of(r1, r2));

        when(reviewRepository.findByProductId(eq(1L), any(Pageable.class))).thenReturn(reviewPage);

        Page<ReviewResponse> result = reviewService.getReviews(1L, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }
}
