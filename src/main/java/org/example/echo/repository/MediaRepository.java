package org.example.echo.repository;

import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaSyncStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<MediaEntity, UUID> {
    Optional<MediaEntity> findByMediaTypeAndTmdbId(MediaKind mediaType, Long tmdbId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select media
        from MediaEntity media
        where media.mediaType = :mediaType
          and media.tmdbId = :tmdbId
        """)
    Optional<MediaEntity> findByMediaTypeAndTmdbIdForUpdate(
        @Param("mediaType") MediaKind mediaType,
        @Param("tmdbId") Long tmdbId
    );

    @Query("""
        select media
        from MediaEntity media
        where media.syncStatus <> :syncedStatus
           or media.lastSyncedAt < :before
        order by
            case when media.syncStatus = :syncedStatus then 1 else 0 end,
            media.lastSyncedAt asc
        """)
    List<MediaEntity> findRefreshCandidates(
        @Param("before") OffsetDateTime before,
        @Param("syncedStatus") MediaSyncStatus syncedStatus,
        Pageable pageable
    );
}
