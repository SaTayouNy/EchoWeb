package org.example.echo.repository;

import org.example.echo.entity.ReviewLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLikeEntity, UUID> {
    long countByReviewReviewId(UUID reviewId);
    Optional<ReviewLikeEntity> findByReviewReviewIdAndUserUserId(UUID reviewId, UUID userId);

    boolean existsByReviewReviewIdAndUserUserId(UUID reviewId, UUID userId);

    long countByUserUserId(UUID userId);

    long countByReviewUserUserId(UUID userId);
}
