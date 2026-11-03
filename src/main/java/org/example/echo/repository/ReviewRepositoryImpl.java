package org.example.echo.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.model.ReviewSortField;
import org.example.echo.model.SortDirectionValue;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ReviewEntity> findFiltered(
        Long tmdbMovieId,
        MediaKind mediaType,
        UUID userId,
        ReviewSortField sort,
        SortDirectionValue order,
        int limit,
        int offset
    ) {
        StringBuilder queryBuilder = new StringBuilder("""
                select review
                from ReviewEntity review
                join fetch review.user
                left join fetch review.media
                where review.status = org.example.echo.entity.ReviewStatus.PUBLISHED
                """);

        if (tmdbMovieId != null) {
            queryBuilder.append(" and review.tmdbMovieId = :tmdbMovieId");
        }
        if (mediaType != null) {
            queryBuilder.append(" and review.mediaType = :mediaType");
        }
        if (userId != null) {
            queryBuilder.append(" and review.user.userId = :userId");
        }

        queryBuilder.append(" order by ")
            .append(primarySortExpression(sort))
            .append(' ')
            .append(order == SortDirectionValue.ASC ? "asc" : "desc")
            .append(", review.reviewId desc");

        TypedQuery<ReviewEntity> query = entityManager.createQuery(queryBuilder.toString(), ReviewEntity.class)
            .setFirstResult(offset)
            .setMaxResults(limit);

        if (tmdbMovieId != null) {
            query.setParameter("tmdbMovieId", tmdbMovieId);
        }
        if (mediaType != null) {
            query.setParameter("mediaType", mediaType);
        }
        if (userId != null) {
            query.setParameter("userId", userId);
        }

        return query.getResultList();
    }

    private String primarySortExpression(ReviewSortField sort) {
        return switch (sort) {
            case CREATED_AT -> "review.createdAt";
            case RATING -> "review.rating";
        };
    }
}
