package org.example.echo.repository;

import org.example.echo.entity.UserRatingEntity;
import org.example.echo.entity.MediaKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRatingRepository extends JpaRepository<UserRatingEntity, UUID> {
    @EntityGraph(attributePaths = {"media"})
    Page<UserRatingEntity> findByUserUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"media"})
    Optional<UserRatingEntity> findByUserUserIdAndMediaTypeAndTmdbId(UUID userId, MediaKind mediaType, Long tmdbId);

    long countByUserUserId(UUID userId);

    @EntityGraph(attributePaths = {"media", "user"})
    Page<UserRatingEntity> findByUserUserIdAndSchemeVersionIsNotNull(UUID userId, Pageable pageable);

    interface ScoreAggregate {
        String getSchemeVersion();
        Long getRatingCount();
        Double getAverageFinalScore();
    }

    interface ParameterAggregate {
        String getSchemeVersion();
        String getParameterCode();
        Double getAverageValue();
    }

    @Query("select r.schemeVersion as schemeVersion, count(r) as ratingCount, avg(r.finalScore) as averageFinalScore " +
        "from UserRatingEntity r where r.mediaType = :kind and r.tmdbId = :tmdbId and r.schemeVersion is not null " +
        "group by r.schemeVersion order by r.schemeVersion")
    List<ScoreAggregate> aggregateScores(@Param("kind") MediaKind kind, @Param("tmdbId") Long tmdbId);

    @Query("select r.schemeVersion as schemeVersion, key(v) as parameterCode, avg(value(v)) as averageValue " +
        "from UserRatingEntity r join r.parameterValues v where r.mediaType = :kind and r.tmdbId = :tmdbId " +
        "and r.schemeVersion is not null group by r.schemeVersion, key(v) order by r.schemeVersion, key(v)")
    List<ParameterAggregate> aggregateParameters(@Param("kind") MediaKind kind, @Param("tmdbId") Long tmdbId);

    void deleteByUserUserIdAndMediaTypeAndTmdbId(UUID userId, MediaKind mediaType, Long tmdbId);
}
