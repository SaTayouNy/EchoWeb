package org.example.echo.service;

import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaSyncStatus;
import org.example.echo.exception.ApiException;
import org.example.echo.model.ApiModels;
import org.example.echo.repository.MediaRepository;
import org.example.echo.tmdb.MediaSyncData;
import org.example.echo.tmdb.TmdbClient;
import org.example.echo.tmdb.TmdbMediaMapper;
import org.example.echo.tmdb.TmdbModels;
import org.example.echo.tmdb.TmdbProperties;
import org.example.echo.tmdb.TmdbTrendingWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class TmdbMediaService {
    private static final Logger log = LoggerFactory.getLogger(TmdbMediaService.class);
    private static final Map<String, String> ORIGINAL_LANGUAGE_TO_LOCALE = Map.ofEntries(
        Map.entry("en", "en-US"),
        Map.entry("ru", "ru-RU"),
        Map.entry("ja", "ja-JP"),
        Map.entry("ko", "ko-KR"),
        Map.entry("fr", "fr-FR"),
        Map.entry("de", "de-DE"),
        Map.entry("es", "es-ES"),
        Map.entry("it", "it-IT"),
        Map.entry("zh", "zh-CN"),
        Map.entry("pt", "pt-BR")
    );

    private final MediaRepository mediaRepository;
    private final MediaPersistenceService mediaPersistenceService;
    private final ReviewMediaLinkService reviewMediaLinkService;
    private final TmdbClient tmdbClient;
    private final TmdbMediaMapper mapper;
    private final TmdbProperties properties;

    public TmdbMediaService(
        MediaRepository mediaRepository,
        MediaPersistenceService mediaPersistenceService,
        ReviewMediaLinkService reviewMediaLinkService,
        TmdbClient tmdbClient,
        TmdbMediaMapper mapper,
        TmdbProperties properties
    ) {
        this.mediaRepository = mediaRepository;
        this.mediaPersistenceService = mediaPersistenceService;
        this.reviewMediaLinkService = reviewMediaLinkService;
        this.tmdbClient = tmdbClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public MediaEntity findExisting(MediaKind mediaType, Long tmdbId) {
        return mediaRepository.findByMediaTypeAndTmdbId(mediaType, tmdbId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found"));
    }

    public MediaEntity getOrSync(MediaKind mediaType, Long tmdbId) {
        return mediaRepository.findByMediaTypeAndTmdbId(mediaType, tmdbId)
            .map(media -> media.getSyncStatus() == MediaSyncStatus.SYNCED ? media : syncMedia(mediaType, tmdbId))
            .orElseGet(() -> syncMedia(mediaType, tmdbId));
    }

    public ApiModels.MediaResponse getMedia(MediaKind mediaType, Long tmdbId) {
        return ApiModels.toMediaResponse(getOrSync(mediaType, tmdbId));
    }

    public MediaEntity findOrCreatePlaceholder(
        MediaKind mediaType,
        Long tmdbId,
        String title,
        LocalDate releaseDate,
        String posterPath
    ) {
        return mediaPersistenceService.findOrCreatePlaceholder(mediaType, tmdbId, title, releaseDate, posterPath);
    }

    public MediaEntity syncMedia(MediaKind mediaType, Long tmdbId) {
        try {
            MediaSyncData data = fetchMediaData(mediaType, tmdbId);
            MediaEntity media = mediaPersistenceService.saveOrUpdate(data);
            try {
                reviewMediaLinkService.attachMediaAndBackfill(media.getMediaId());
            } catch (RuntimeException ex) {
                log.warn("Failed to backfill reviews for {} {}", mediaType, tmdbId, ex);
            }
            return media;
        } catch (RuntimeException ex) {
            mediaPersistenceService.markSyncFailedIfExists(mediaType, tmdbId);
            throw ex;
        }
    }

    public ApiModels.MediaSearchResponse searchMovies(String query, Integer page) {
        return fromTmdb("search movies", () ->
            mapper.toMovieSearchResponse(tmdbClient.searchMovies(query.trim(), page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse searchTv(String query, Integer page) {
        return fromTmdb("search tv", () ->
            mapper.toTvSearchResponse(tmdbClient.searchTv(query.trim(), page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse getPopularMovies(Integer page) {
        return fromTmdb("popular movies", () ->
            mapper.toMovieSearchResponse(tmdbClient.getPopularMovies(page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse getPopularTv(Integer page) {
        return fromTmdb("popular tv", () ->
            mapper.toTvSearchResponse(tmdbClient.getPopularTv(page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse getTrendingMovies(TmdbTrendingWindow window, Integer page) {
        return fromTmdb("trending movies", () ->
            mapper.toMovieSearchResponse(tmdbClient.getTrendingMovies(window, page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse getTrendingTv(TmdbTrendingWindow window, Integer page) {
        return fromTmdb("trending tv", () ->
            mapper.toTvSearchResponse(tmdbClient.getTrendingTv(window, page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse getMovieRecommendations(Long tmdbId, Integer page) {
        return fromTmdb("movie recommendations", () ->
            mapper.toMovieSearchResponse(tmdbClient.getMovieRecommendations(tmdbId, page, properties.getLanguage()))
        );
    }

    public ApiModels.MediaSearchResponse getTvRecommendations(Long tmdbId, Integer page) {
        return fromTmdb("tv recommendations", () ->
            mapper.toTvSearchResponse(tmdbClient.getTvRecommendations(tmdbId, page, properties.getLanguage()))
        );
    }

    @Scheduled(fixedDelayString = "${app.tmdb.refresh-fixed-delay-ms:86400000}")
    public void refreshStaleMediaScheduled() {
        refreshStaleMedia();
    }

    public void refreshStaleMedia() {
        OffsetDateTime staleBefore = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getRefreshStaleAfter());
        List<MediaEntity> staleMedia = mediaRepository.findRefreshCandidates(
            staleBefore,
            MediaSyncStatus.SYNCED,
            PageRequest.of(0, properties.getRefreshBatchSize())
        );

        int refreshed = 0;
        int failed = 0;
        for (MediaEntity media : staleMedia) {
            try {
                syncMedia(media.getMediaType(), media.getTmdbId());
                refreshed++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Failed to refresh TMDB media {} {}", media.getMediaType(), media.getTmdbId(), ex);
            }
        }

        if (!staleMedia.isEmpty()) {
            log.info("TMDB stale media refresh batch completed: batchSize={}, refreshed={}, failed={}", staleMedia.size(), refreshed, failed);
        }
    }

    private MediaSyncData fetchMediaData(MediaKind mediaType, Long tmdbId) {
        return switch (mediaType) {
            case MOVIE -> fetchMovieData(tmdbId);
            case TV -> fetchTvData(tmdbId);
        };
    }

    private MediaSyncData fetchMovieData(Long tmdbId) {
        TmdbModels.MovieDetails localized = tmdbClient.getMovieDetails(tmdbId, properties.getLanguage());
        if (localized == null || !tmdbId.equals(localized.id())) {
            throw invalidDetails();
        }
        TmdbModels.MovieDetails original = localized;
        String originalLanguage = languageForOriginal(localized.originalLanguage());
        if (!sameLanguage(properties.getLanguage(), originalLanguage)) {
            try {
                TmdbModels.MovieDetails candidate = tmdbClient.getMovieDetails(tmdbId, originalLanguage);
                if (candidate != null && tmdbId.equals(candidate.id())) {
                    original = candidate;
                } else {
                    log.warn("TMDB original-language movie response has wrong ID for {}", tmdbId);
                }
            } catch (ApiException ex) {
                log.warn("Unable to load original-language movie details for {}: {}", tmdbId, ex.getCode());
            }
        }
        return mapper.toMovieData(localized, original);
    }

    private MediaSyncData fetchTvData(Long tmdbId) {
        TmdbModels.TvDetails localized = tmdbClient.getTvDetails(tmdbId, properties.getLanguage());
        if (localized == null || !tmdbId.equals(localized.id())) {
            throw invalidDetails();
        }
        TmdbModels.TvDetails original = localized;
        String originalLanguage = languageForOriginal(localized.originalLanguage());
        if (!sameLanguage(properties.getLanguage(), originalLanguage)) {
            try {
                TmdbModels.TvDetails candidate = tmdbClient.getTvDetails(tmdbId, originalLanguage);
                if (candidate != null && tmdbId.equals(candidate.id())) {
                    original = candidate;
                } else {
                    log.warn("TMDB original-language TV response has wrong ID for {}", tmdbId);
                }
            } catch (ApiException ex) {
                log.warn("Unable to load original-language TV details for {}: {}", tmdbId, ex.getCode());
            }
        }
        return mapper.toTvData(localized, original);
    }

    private ApiException invalidDetails() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
    }

    private String languageForOriginal(String originalLanguage) {
        if (originalLanguage == null || originalLanguage.isBlank()) {
            return properties.getFallbackLanguage();
        }
        return ORIGINAL_LANGUAGE_TO_LOCALE.getOrDefault(
            originalLanguage.toLowerCase(Locale.ROOT),
            originalLanguage
        );
    }

    private boolean sameLanguage(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private <T> T fromTmdb(String operation, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Unexpected TMDB {} failure", operation, ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
        }
    }
}
