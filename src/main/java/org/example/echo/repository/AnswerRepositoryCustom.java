package org.example.echo.repository;

import org.example.echo.entity.AnswerEntity;

import java.util.List;
import java.util.UUID;

public interface AnswerRepositoryCustom {
    List<AnswerEntity> findByReviewReviewId(UUID reviewId, int limit, int offset);
}
