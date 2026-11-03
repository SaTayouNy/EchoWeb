package org.example.echo.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.echo.entity.AnswerEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class AnswerRepositoryImpl implements AnswerRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<AnswerEntity> findByReviewReviewId(UUID reviewId, int limit, int offset) {
        return entityManager.createQuery("""
                select answer
                from AnswerEntity answer
                join fetch answer.user
                join fetch answer.review
                where answer.review.reviewId = :reviewId
                order by answer.createdAt desc, answer.answerId desc
                """, AnswerEntity.class)
            .setParameter("reviewId", reviewId)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    }
}
