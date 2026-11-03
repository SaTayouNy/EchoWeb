package org.example.echo.repository;

import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID>, ReviewRepositoryCustom {
    boolean existsByUserRatingRatingId(UUID ratingId);

    @Modifying(flushAutomatically = true)
    @Query("update ReviewEntity r set r.status = org.example.echo.entity.ReviewStatus.PENDING_MODERATION, r.publishedAt = null, r.updatedAt = :now where r.userRating.ratingId = :ratingId and r.status = org.example.echo.entity.ReviewStatus.PUBLISHED")
    int requeuePublishedByRatingId(@Param("ratingId") UUID ratingId, @Param("now") java.time.OffsetDateTime now);

    long countByUserUserIdAndStatus(UUID userId, org.example.echo.entity.ReviewStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewEntity r where r.reviewId = :reviewId")
    Optional<ReviewEntity> findByIdForUpdate(@Param("reviewId") UUID reviewId);
    boolean existsByUserUserIdAndMediaTypeAndTmdbMovieId(UUID userId, MediaKind mediaType, Long tmdbMovieId);

    long countByUserUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "media"})
    Optional<ReviewEntity> findByReviewId(UUID reviewId);

    @Query("""
        select count(r) from ReviewEntity r
        where (:tmdbMovieId is null or r.tmdbMovieId = :tmdbMovieId)
          and r.status = org.example.echo.entity.ReviewStatus.PUBLISHED
          and (:mediaType is null or r.mediaType = :mediaType)
          and (:userId is null or r.user.userId = :userId)
        """)
    long countFiltered(
        @Param("tmdbMovieId") Long tmdbMovieId,
        @Param("mediaType") MediaKind mediaType,
        @Param("userId") UUID userId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ReviewEntity review
        set review.media = :media,
            review.movieTitle = case
                when review.movieTitle = 'Untitled' then :title
                else review.movieTitle
            end,
            review.movieReleaseDate = case
                when review.movieReleaseDate is null then :releaseDate
                else review.movieReleaseDate
            end,
            review.moviePosterPath = case
                when review.moviePosterPath is null then :posterPath
                else review.moviePosterPath
            end
        where review.mediaType = :mediaType
          and review.tmdbMovieId = :tmdbMovieId
        """)
    int attachMediaAndBackfillMetadata(
        @Param("media") MediaEntity media,
        @Param("mediaType") MediaKind mediaType,
        @Param("tmdbMovieId") Long tmdbMovieId,
        @Param("title") String title,
        @Param("releaseDate") java.time.LocalDate releaseDate,
        @Param("posterPath") String posterPath
    );
}
