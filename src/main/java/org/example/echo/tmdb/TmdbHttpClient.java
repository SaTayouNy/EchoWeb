package org.example.echo.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

@Component
@ConditionalOnProperty(name = "app.tmdb.provider", havingValue = "http", matchIfMissing = true)
public class TmdbHttpClient implements TmdbClient {
    private static final Logger log = LoggerFactory.getLogger(TmdbHttpClient.class);
    private static final int RESPONSE_LOG_SNIPPET_LENGTH = 300;

    private final TmdbProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TmdbHttpClient(TmdbProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.getConnectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "identity")
            .build();
    }

    @Override
    public TmdbModels.MovieDetails getMovieDetails(Long tmdbId, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/movie/{tmdbId}")
                .queryParam("language", language)
                .queryParam("append_to_response", "credits")
                .build(tmdbId))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.MovieDetails.class);
    }

    @Override
    public TmdbModels.TvDetails getTvDetails(Long tmdbId, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/tv/{tmdbId}")
                .queryParam("language", language)
                .queryParam("append_to_response", "credits")
                .build(tmdbId))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.TvDetails.class);
    }

    @Override
    public TmdbModels.MovieSearchResponse searchMovies(String query, Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/search/movie")
                .queryParam("query", query)
                .queryParam("page", page)
                .queryParam("language", language)
                .queryParam("include_adult", false)
                .build())
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.MovieSearchResponse.class);
    }

    @Override
    public TmdbModels.TvSearchResponse searchTv(String query, Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/search/tv")
                .queryParam("query", query)
                .queryParam("page", page)
                .queryParam("language", language)
                .queryParam("include_adult", false)
                .build())
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.TvSearchResponse.class);
    }

    @Override
    public TmdbModels.MovieSearchResponse getPopularMovies(Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/movie/popular")
                .queryParam("page", page)
                .queryParam("language", language)
                .build())
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.MovieSearchResponse.class);
    }

    @Override
    public TmdbModels.TvSearchResponse getPopularTv(Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/tv/popular")
                .queryParam("page", page)
                .queryParam("language", language)
                .build())
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.TvSearchResponse.class);
    }

    @Override
    public TmdbModels.MovieSearchResponse getTrendingMovies(TmdbTrendingWindow window, Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/trending/movie/{window}")
                .queryParam("page", page)
                .queryParam("language", language)
                .build(window.externalValue()))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.MovieSearchResponse.class);
    }

    @Override
    public TmdbModels.TvSearchResponse getTrendingTv(TmdbTrendingWindow window, Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/trending/tv/{window}")
                .queryParam("page", page)
                .queryParam("language", language)
                .build(window.externalValue()))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.TvSearchResponse.class);
    }

    @Override
    public TmdbModels.MovieSearchResponse getMovieRecommendations(Long tmdbId, Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/movie/{tmdbId}/recommendations")
                .queryParam("page", page)
                .queryParam("language", language)
                .build(tmdbId))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.MovieSearchResponse.class);
    }

    @Override
    public TmdbModels.TvSearchResponse getTvRecommendations(Long tmdbId, Integer page, String language) {
        return execute(() -> restClient.get()
            .uri(uri -> uri.path("/tv/{tmdbId}/recommendations")
                .queryParam("page", page)
                .queryParam("language", language)
                .build(tmdbId))
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .retrieve()
            .body(byte[].class), TmdbModels.TvSearchResponse.class);
    }

    private <T> T execute(Supplier<byte[]> call, Class<T> responseType) {
        ensureConfigured();
        int maxAttempts = Math.max(properties.getRetryMaxAttempts(), 1);
        ApiException lastRetryableException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] response = call.get();
                if (response == null || response.length == 0) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an empty response");
                }
                return readJson(response, responseType);
            } catch (HttpClientErrorException.NotFound ex) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media not found in TMDB");
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_AUTH_FAILED", "TMDB authentication failed");
            } catch (HttpClientErrorException.TooManyRequests ex) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_RATE_LIMITED", "TMDB rate limit exceeded");
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().is5xxServerError()) {
                    log.warn("TMDB server error: status={}, attempt={}/{}", ex.getStatusCode(), attempt, maxAttempts, ex);
                    lastRetryableException = new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB is unavailable");
                    if (attempt < maxAttempts) {
                        pauseBeforeRetry(attempt);
                        continue;
                    }
                    throw lastRetryableException;
                }
                log.warn("Unexpected TMDB response: status={}", ex.getStatusCode(), ex);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "Unexpected TMDB response");
            } catch (ResourceAccessException ex) {
                log.warn("Unable to reach TMDB at {}, attempt={}/{}", properties.getBaseUrl(), attempt, maxAttempts, ex);
                lastRetryableException = new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB is unavailable");
                if (attempt < maxAttempts) {
                    pauseBeforeRetry(attempt);
                    continue;
                }
                throw lastRetryableException;
            } catch (HttpMessageConversionException | RestClientException ex) {
                log.warn("Invalid TMDB response", ex);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
            }
        }

        throw lastRetryableException == null
            ? new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB is unavailable")
            : lastRetryableException;
    }

    private <T> T readJson(byte[] response, Class<T> responseType) {
        byte[] json = decodeResponse(response);
        try {
            T result = objectMapper.readValue(json, responseType);
            validateResponse(result);
            return result;
        } catch (IOException ex) {
            log.warn("Invalid TMDB JSON for {}: {}", responseType.getSimpleName(), snippet(json), ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
        }
    }

    private void validateResponse(Object response) {
        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
        }
        boolean valid = switch (response) {
            case TmdbModels.MovieDetails details -> details.id() != null && details.id() > 0
                && hasTitle(details.title(), details.originalTitle());
            case TmdbModels.TvDetails details -> details.id() != null && details.id() > 0
                && hasTitle(details.name(), details.originalName());
            case TmdbModels.MovieSearchResponse page -> validPage(page.page(), page.totalPages(), page.totalResults(), page.results());
            case TmdbModels.TvSearchResponse page -> validPage(page.page(), page.totalPages(), page.totalResults(), page.results());
            default -> false;
        };
        if (!valid) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
        }
    }

    private boolean hasTitle(String localized, String original) {
        return (localized != null && !localized.isBlank()) || (original != null && !original.isBlank());
    }

    private boolean validPage(Integer page, Integer totalPages, Long totalResults, Object results) {
        return page != null && page > 0 && totalPages != null && totalPages >= 0
            && totalResults != null && totalResults >= 0 && results != null;
    }

    private byte[] decodeResponse(byte[] response) {
        if (!isGzip(response)) {
            return response;
        }

        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(response))) {
            return gzip.readAllBytes();
        } catch (IOException ex) {
            log.warn("Invalid gzipped TMDB response", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_BAD_RESPONSE", "TMDB returned an invalid response");
        }
    }

    private boolean isGzip(byte[] response) {
        return response.length >= 2
            && (response[0] & 0xFF) == 0x1F
            && (response[1] & 0xFF) == 0x8B;
    }

    private String snippet(byte[] response) {
        String normalized = new String(response, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= RESPONSE_LOG_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, RESPONSE_LOG_SNIPPET_LENGTH) + "...";
    }

    private void pauseBeforeRetry(int attempt) {
        Duration retryDelay = properties.getRetryBackoff().multipliedBy(Math.max(attempt, 1));
        if (retryDelay.isZero() || retryDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "TMDB retry was interrupted");
        }
    }

    private String bearer() {
        return "Bearer " + properties.getAccessToken().trim();
    }

    private void ensureConfigured() {
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_CONFIGURATION_ERROR", "TMDB access token is not configured");
        }
    }
}
