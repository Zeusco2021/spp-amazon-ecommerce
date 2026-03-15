package com.ecommerce.recommendation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_preferences",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_preference_user_category", columnNames = {"user_id", "category_id"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Double score = 0.0;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
