package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    List<UserPreference> findByUserIdOrderByScoreDesc(Long userId);

    Optional<UserPreference> findByUserIdAndCategoryId(Long userId, Long categoryId);

    @Query("SELECT p.categoryId FROM UserPreference p WHERE p.userId = :userId")
    List<Long> findCategoryIdsByUserId(@Param("userId") Long userId);
}
