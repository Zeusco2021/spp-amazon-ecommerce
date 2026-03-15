package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.repository.UserActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collaborative filtering service using Jaccard similarity.
 * Finds users with similar purchase behavior to generate recommendations.
 * Requirements: 11.2
 */
@Service
@Transactional(readOnly = true)
public class CollaborativeFilteringService {

    private static final Logger logger = LoggerFactory.getLogger(CollaborativeFilteringService.class);
    private static final int MAX_SIMILAR_USERS = 10;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.1;

    private final UserActivityRepository userActivityRepository;

    public CollaborativeFilteringService(UserActivityRepository userActivityRepository) {
        this.userActivityRepository = userActivityRepository;
    }

    /**
     * Calculate Jaccard similarity between two users based on their purchased products.
     * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     * Requirements: 11.2
     */
    public double calculateJaccardSimilarity(Long userId1, Long userId2) {
        Set<Long> products1 = new HashSet<>(userActivityRepository.findPurchasedProductIdsByUserId(userId1));
        Set<Long> products2 = new HashSet<>(userActivityRepository.findPurchasedProductIdsByUserId(userId2));

        if (products1.isEmpty() && products2.isEmpty()) return 0.0;

        Set<Long> intersection = new HashSet<>(products1);
        intersection.retainAll(products2);

        Set<Long> union = new HashSet<>(products1);
        union.addAll(products2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    /**
     * Find users similar to the given user, sorted by Jaccard similarity descending.
     * Returns up to MAX_SIMILAR_USERS users with similarity >= MIN_SIMILARITY_THRESHOLD.
     * Requirements: 11.2
     */
    public List<Long> findSimilarUsers(Long userId) {
        // Get all products this user purchased
        List<Long> userProducts = userActivityRepository.findPurchasedProductIdsByUserId(userId);
        if (userProducts.isEmpty()) {
            logger.debug("No purchase history for userId={}, cannot find similar users", userId);
            return Collections.emptyList();
        }

        // Find candidate users who purchased at least one of the same products
        Set<Long> candidateUsers = new HashSet<>();
        for (Long productId : userProducts) {
            List<Long> otherUsers = userActivityRepository.findUserIdsByProductIdAndPurchase(productId);
            candidateUsers.addAll(otherUsers);
        }
        candidateUsers.remove(userId); // exclude self

        if (candidateUsers.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate Jaccard similarity for each candidate
        Map<Long, Double> similarityScores = new HashMap<>();
        for (Long candidateId : candidateUsers) {
            double similarity = calculateJaccardSimilarity(userId, candidateId);
            if (similarity >= MIN_SIMILARITY_THRESHOLD) {
                similarityScores.put(candidateId, similarity);
            }
        }

        // Sort by similarity descending, return top MAX_SIMILAR_USERS
        return similarityScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(MAX_SIMILAR_USERS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get products purchased by similar users that the target user hasn't purchased.
     * Requirements: 11.2, 11.3
     */
    public List<Long> getProductsFromSimilarUsers(Long userId, List<Long> similarUserIds) {
        Set<Long> alreadyPurchased = new HashSet<>(userActivityRepository.findPurchasedProductIdsByUserId(userId));

        Set<Long> recommendedProducts = new LinkedHashSet<>();
        for (Long similarUserId : similarUserIds) {
            List<Long> theirProducts = userActivityRepository.findPurchasedProductIdsByUserId(similarUserId);
            for (Long productId : theirProducts) {
                if (!alreadyPurchased.contains(productId)) {
                    recommendedProducts.add(productId);
                }
            }
        }
        return new ArrayList<>(recommendedProducts);
    }
}
