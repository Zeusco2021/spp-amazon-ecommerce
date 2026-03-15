package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.repository.UserActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborativeFilteringServiceTest {

    @Mock
    private UserActivityRepository userActivityRepository;

    @InjectMocks
    private CollaborativeFilteringService collaborativeFilteringService;

    // --- calculateJaccardSimilarity ---

    @Test
    void jaccardSimilarity_identicalSets_returnsOne() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L, 20L, 30L));
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(List.of(10L, 20L, 30L));

        double similarity = collaborativeFilteringService.calculateJaccardSimilarity(1L, 2L);

        assertEquals(1.0, similarity, 0.001);
    }

    @Test
    void jaccardSimilarity_disjointSets_returnsZero() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L, 20L));
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(List.of(30L, 40L));

        double similarity = collaborativeFilteringService.calculateJaccardSimilarity(1L, 2L);

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void jaccardSimilarity_partialOverlap_returnsCorrectValue() {
        // |intersection| = 1 (product 10), |union| = 3 (10, 20, 30) → 1/3
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L, 20L));
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(List.of(10L, 30L));

        double similarity = collaborativeFilteringService.calculateJaccardSimilarity(1L, 2L);

        assertEquals(1.0 / 3.0, similarity, 0.001);
    }

    @Test
    void jaccardSimilarity_bothEmpty_returnsZero() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(Collections.emptyList());
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(Collections.emptyList());

        double similarity = collaborativeFilteringService.calculateJaccardSimilarity(1L, 2L);

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void jaccardSimilarity_oneEmpty_returnsZero() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L, 20L));
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(Collections.emptyList());

        double similarity = collaborativeFilteringService.calculateJaccardSimilarity(1L, 2L);

        assertEquals(0.0, similarity, 0.001);
    }

    // --- findSimilarUsers ---

    @Test
    void findSimilarUsers_noPurchaseHistory_returnsEmpty() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(Collections.emptyList());

        List<Long> result = collaborativeFilteringService.findSimilarUsers(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findSimilarUsers_withSimilarUser_returnsSortedByScore() {
        // User 1 purchased products 10, 20
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L, 20L));
        // Product 10 was also purchased by user 2
        when(userActivityRepository.findUserIdsByProductIdAndPurchase(10L)).thenReturn(List.of(2L));
        // Product 20 was also purchased by user 3
        when(userActivityRepository.findUserIdsByProductIdAndPurchase(20L)).thenReturn(List.of(3L));
        // User 2 purchased 10, 20 (high similarity = 1.0)
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(List.of(10L, 20L));
        // User 3 purchased only 20 (lower similarity = 1/3)
        when(userActivityRepository.findPurchasedProductIdsByUserId(3L)).thenReturn(List.of(20L, 30L));

        List<Long> result = collaborativeFilteringService.findSimilarUsers(1L);

        assertFalse(result.isEmpty());
        // User 2 has higher similarity (1.0) than user 3 (1/3), so user 2 should come first
        assertEquals(2L, result.get(0));
    }

    @Test
    void findSimilarUsers_excludesSelf() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L));
        when(userActivityRepository.findUserIdsByProductIdAndPurchase(10L)).thenReturn(List.of(1L, 2L));
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(List.of(10L));

        List<Long> result = collaborativeFilteringService.findSimilarUsers(1L);

        assertFalse(result.contains(1L));
    }

    // --- getProductsFromSimilarUsers ---

    @Test
    void getProductsFromSimilarUsers_excludesAlreadyPurchased() {
        // User 1 already purchased product 10
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L));
        // Similar user 2 purchased 10 and 20
        when(userActivityRepository.findPurchasedProductIdsByUserId(2L)).thenReturn(List.of(10L, 20L));

        List<Long> result = collaborativeFilteringService.getProductsFromSimilarUsers(1L, List.of(2L));

        assertFalse(result.contains(10L)); // already purchased
        assertTrue(result.contains(20L));  // new recommendation
    }

    @Test
    void getProductsFromSimilarUsers_noSimilarUsers_returnsEmpty() {
        when(userActivityRepository.findPurchasedProductIdsByUserId(1L)).thenReturn(List.of(10L));

        List<Long> result = collaborativeFilteringService.getProductsFromSimilarUsers(1L, Collections.emptyList());

        assertTrue(result.isEmpty());
    }
}
