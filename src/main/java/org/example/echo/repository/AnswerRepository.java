package org.example.echo.repository;

import org.example.echo.entity.AnswerEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<AnswerEntity, UUID>, AnswerRepositoryCustom {
    @EntityGraph(attributePaths = {"user", "review"})
    Optional<AnswerEntity> findByAnswerId(UUID answerId);

    long countByReviewReviewId(UUID reviewId);

    long countByUserUserIdAndAuthorLikedTrue(UUID userId);
}
