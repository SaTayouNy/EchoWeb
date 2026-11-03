package org.example.echo.service;

import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaSyncStatus;
import org.example.echo.exception.ApiException;
import org.example.echo.repository.MediaRepository;
import org.example.echo.tmdb.MediaSyncData;
import org.example.echo.util.StringNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

@Service
public class MediaPersistenceService {
    private static final String PLACEHOLDER_TITLE = "Untitled";
    private static final String MEDIA_TYPE_TMDB_ID_UNIQUE_CONSTRAINT = "uk_media_type_tmdb_id";

    private final MediaRepository mediaRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate independentTransactionTemplate;

    public MediaPersistenceService(MediaRepository mediaRepository, PlatformTransactionManager transactionManager) {
        this.mediaRepository = mediaRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.independentTransactionTemplate = new TransactionTemplate(transactionManager);
        this.independentTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public MediaEntity saveOrUpdate(MediaSyncData data) {
        return saveOrUpdateWithRetry(data);
    }

    public MediaEntity findOrCreatePlaceholder(
        MediaKind mediaType,
        Long tmdbId,
        String title,
        LocalDate releaseDate,
        String posterPath
    ) {
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status ->
                insertOrUpdatePlaceholder(mediaType, tmdbId, title, releaseDate, posterPath)
            ));
        } catch (DataIntegrityViolationException ex) {
            if (!isRetryableUniqueConflict(ex)) {
                throw ex;
            }
            return Objects.requireNonNull(transactionTemplate.execute(status ->
                updateExistingPlaceholder(mediaType, tmdbId, title, releaseDate, posterPath)
            ));
        }
    }

    public void markSyncFailedIfExists(MediaKind mediaType, Long tmdbId) {
        independentTransactionTemplate.executeWithoutResult(status -> mediaRepository.findByMediaTypeAndTmdbIdForUpdate(mediaType, tmdbId)
            .ifPresent(media -> {
                if (media.getSyncStatus() != MediaSyncStatus.SYNCED) {
                    media.setSyncStatus(MediaSyncStatus.FAILED);
                    mediaRepository.saveAndFlush(media);
                }
            }));
    }

    private MediaEntity saveOrUpdateWithRetry(MediaSyncData data) {
        try {
            return Objects.requireNonNull(independentTransactionTemplate.execute(status -> insertOrUpdate(data)));
        } catch (DataIntegrityViolationException ex) {
            if (!isRetryableUniqueConflict(ex)) {
                throw ex;
            }
            return Objects.requireNonNull(independentTransactionTemplate.execute(status -> updateExisting(data)));
        }
    }

    private MediaEntity insertOrUpdate(MediaSyncData data) {
        MediaEntity media = mediaRepository.findByMediaTypeAndTmdbIdForUpdate(data.mediaType(), data.tmdbId())
            .orElseGet(MediaEntity::new);
        apply(media, data);
        return mediaRepository.saveAndFlush(media);
    }

    private MediaEntity updateExisting(MediaSyncData data) {
        MediaEntity media = mediaRepository.findByMediaTypeAndTmdbIdForUpdate(data.mediaType(), data.tmdbId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Media sync conflict"));
        apply(media, data);
        return mediaRepository.saveAndFlush(media);
    }

    private MediaEntity insertOrUpdatePlaceholder(
        MediaKind mediaType,
        Long tmdbId,
        String title,
        LocalDate releaseDate,
        String posterPath
    ) {
        MediaEntity media = mediaRepository.findByMediaTypeAndTmdbIdForUpdate(mediaType, tmdbId)
            .orElseGet(MediaEntity::new);
        if (media.getMediaId() == null) {
            applyPlaceholder(media, mediaType, tmdbId, title, releaseDate, posterPath);
        } else if (media.getSyncStatus() != MediaSyncStatus.SYNCED) {
            mergePlaceholder(media, title, releaseDate, posterPath);
        }
        return mediaRepository.saveAndFlush(media);
    }

    private MediaEntity updateExistingPlaceholder(
        MediaKind mediaType,
        Long tmdbId,
        String title,
        LocalDate releaseDate,
        String posterPath
    ) {
        MediaEntity media = mediaRepository.findByMediaTypeAndTmdbIdForUpdate(mediaType, tmdbId)
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Media sync conflict"));
        if (media.getSyncStatus() != MediaSyncStatus.SYNCED) {
            mergePlaceholder(media, title, releaseDate, posterPath);
        }
        return mediaRepository.saveAndFlush(media);
    }

    private void apply(MediaEntity media, MediaSyncData data) {
        media.setMediaType(data.mediaType());
        media.setTmdbId(data.tmdbId());
        media.setTitle(data.title());
        media.setOriginalTitle(data.originalTitle());
        media.setOverview(data.overview());
        media.setOriginalOverview(data.originalOverview());
        media.setReleaseYear(data.releaseYear());
        media.setReleaseDate(data.releaseDate());
        media.setRuntimeMinutes(data.runtimeMinutes());
        media.setPosterPath(data.posterPath());
        media.setPosterUrl(data.posterUrl());
        media.setDirector(data.director());
        media.setCastMembers(data.castMembers());
        media.setCastDetails(data.castDetails());
        media.setCrewDetails(data.crewDetails());
        media.setGenres(data.genres());
        media.setCountries(data.countries());
        media.setSyncStatus(MediaSyncStatus.SYNCED);
        media.setLastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void applyPlaceholder(
        MediaEntity media,
        MediaKind mediaType,
        Long tmdbId,
        String title,
        LocalDate releaseDate,
        String posterPath
    ) {
        media.setMediaType(mediaType);
        media.setTmdbId(tmdbId);
        media.setTitle(defaultIfBlank(title, PLACEHOLDER_TITLE));
        media.setOriginalTitle(null);
        media.setOverview(null);
        media.setOriginalOverview(null);
        media.setReleaseDate(releaseDate);
        media.setReleaseYear(releaseDate == null ? null : releaseDate.getYear());
        media.setRuntimeMinutes(null);
        media.setPosterPath(StringNormalizer.blankToNull(posterPath));
        media.setPosterUrl(null);
        media.setDirector(null);
        media.setCastMembers(null);
        media.setCastDetails(null);
        media.setCrewDetails(null);
        media.setGenres(null);
        media.setCountries(null);
        media.setSyncStatus(MediaSyncStatus.PENDING);
        media.setLastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void mergePlaceholder(MediaEntity media, String title, LocalDate releaseDate, String posterPath) {
        if (!StringNormalizer.hasText(media.getTitle()) || PLACEHOLDER_TITLE.equals(media.getTitle())) {
            media.setTitle(defaultIfBlank(title, PLACEHOLDER_TITLE));
        }
        if (media.getReleaseDate() == null) {
            media.setReleaseDate(releaseDate);
            media.setReleaseYear(releaseDate == null ? media.getReleaseYear() : releaseDate.getYear());
        }
        if (!StringNormalizer.hasText(media.getPosterPath())) {
            media.setPosterPath(StringNormalizer.blankToNull(posterPath));
        }
        if (media.getSyncStatus() == null) {
            media.setSyncStatus(MediaSyncStatus.PENDING);
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = StringNormalizer.firstNonBlank(value);
        return normalized == null ? fallback : normalized;
    }

    private boolean isRetryableUniqueConflict(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolation) {
                if (hasUniqueSqlState(constraintViolation.getSQLException())) {
                    return true;
                }
                String constraintName = constraintViolation.getConstraintName();
                if (isMediaTypeTmdbIdUniqueConstraint(constraintName)) {
                    return true;
                }
            }
            if (current instanceof SQLException sqlException && hasUniqueSqlState(sqlException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasUniqueSqlState(SQLException sqlException) {
        return sqlException != null && "23505".equals(sqlException.getSQLState());
    }

    private boolean isMediaTypeTmdbIdUniqueConstraint(String constraintName) {
        if (constraintName == null) {
            return false;
        }
        String normalized = constraintName.toLowerCase(Locale.ROOT);
        return MEDIA_TYPE_TMDB_ID_UNIQUE_CONSTRAINT.equals(normalized)
            || normalized.endsWith("." + MEDIA_TYPE_TMDB_ID_UNIQUE_CONSTRAINT);
    }
}
