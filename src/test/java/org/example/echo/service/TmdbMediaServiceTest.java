package org.example.echo.service;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.repository.MediaRepository;
import org.example.echo.tmdb.MediaSyncData;
import org.example.echo.tmdb.TmdbClient;
import org.example.echo.tmdb.TmdbMediaMapper;
import org.example.echo.tmdb.TmdbModels;
import org.example.echo.tmdb.TmdbProperties;
import org.example.echo.tmdb.TmdbTrendingWindow;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class TmdbMediaServiceTest {
    @Test
    void primaryMovieDetailsRemainUsableWhenOriginalLanguageRequestFails() {
        TmdbClient client = mock(TmdbClient.class);
        MediaPersistenceService persistence = mock(MediaPersistenceService.class);
        ReviewMediaLinkService links = mock(ReviewMediaLinkService.class);
        TmdbProperties properties = new TmdbProperties();
        TmdbModels.MovieDetails localized = new TmdbModels.MovieDetails(
            603L, "Матрица", "The Matrix", "en", "Русское описание", "1999-03-31",
            136, "/matrix.jpg", List.of(), List.of(), null
        );
        when(client.getMovieDetails(603L, "ru-RU")).thenReturn(localized);
        when(client.getMovieDetails(603L, "en-US"))
            .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB is unavailable"));
        MediaEntity saved = new MediaEntity();
        saved.setMediaId(UUID.randomUUID());
        when(persistence.saveOrUpdate(any())).thenReturn(saved);
        TmdbMediaService service = new TmdbMediaService(
            mock(MediaRepository.class), persistence, links, client, new TmdbMediaMapper(properties), properties
        );

        assertThat(service.syncMedia(MediaKind.MOVIE, 603L)).isSameAs(saved);
        ArgumentCaptor<MediaSyncData> data = ArgumentCaptor.forClass(MediaSyncData.class);
        verify(persistence).saveOrUpdate(data.capture());
        assertThat(data.getValue().overview()).isEqualTo("Русское описание");
        assertThat(data.getValue().originalOverview()).isEqualTo("Русское описание");
    }

    @Test
    void mismatchedTmdbIdCannotBeSavedAsRequestedMovie() {
        TmdbClient client = mock(TmdbClient.class);
        MediaPersistenceService persistence = mock(MediaPersistenceService.class);
        TmdbProperties properties = new TmdbProperties();
        when(client.getMovieDetails(603L, "ru-RU")).thenReturn(new TmdbModels.MovieDetails(
            999L, "Wrong film", "Wrong film", "en", "Wrong overview", "1999-03-31",
            100, null, List.of(), List.of(), null
        ));
        TmdbMediaService service = new TmdbMediaService(
            mock(MediaRepository.class), persistence, mock(ReviewMediaLinkService.class), client,
            new TmdbMediaMapper(properties), properties
        );

        assertThatThrownBy(() -> service.syncMedia(MediaKind.MOVIE, 603L))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("TMDB_BAD_RESPONSE"));
        verify(persistence, never()).saveOrUpdate(any());
    }

    @Test
    void searchMoviesWrapsUnexpectedTmdbFailures() {
        TmdbProperties properties = new TmdbProperties();
        properties.setAccessToken("test-token");
        TmdbMediaService service = new TmdbMediaService(
            null,
            null,
            null,
            new FailingTmdbClient(),
            new TmdbMediaMapper(properties),
            properties
        );

        assertThatThrownBy(() -> service.searchMovies("matrix", 1))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(ex.getCode()).isEqualTo("TMDB_BAD_RESPONSE");
            });
    }

    private static final class FailingTmdbClient implements TmdbClient {
        @Override
        public TmdbModels.MovieDetails getMovieDetails(Long tmdbId, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.TvDetails getTvDetails(Long tmdbId, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.MovieSearchResponse searchMovies(String query, Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.TvSearchResponse searchTv(String query, Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.MovieSearchResponse getPopularMovies(Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.TvSearchResponse getPopularTv(Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.MovieSearchResponse getTrendingMovies(TmdbTrendingWindow window, Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.TvSearchResponse getTrendingTv(TmdbTrendingWindow window, Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.MovieSearchResponse getMovieRecommendations(Long tmdbId, Integer page, String language) {
            throw failure();
        }

        @Override
        public TmdbModels.TvSearchResponse getTvRecommendations(Long tmdbId, Integer page, String language) {
            throw failure();
        }

        private RuntimeException failure() {
            return new IllegalStateException("unexpected tmdb failure");
        }
    }
}
