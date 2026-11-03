package org.example.echo.tmdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.echo.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Exercises actual HTTP responses from a local server; no TMDB credentials are needed. */
class TmdbHttpClientFailureTest {
    @ParameterizedTest
    @CsvSource({
        "401,502,TMDB_AUTH_FAILED,1",
        "403,502,TMDB_AUTH_FAILED,1",
        "404,404,MEDIA_NOT_FOUND,1",
        "429,503,TMDB_RATE_LIMITED,1",
        "500,502,TMDB_UNAVAILABLE,2",
        "503,502,TMDB_UNAVAILABLE,2",
        "400,502,TMDB_BAD_RESPONSE,1"
    })
    void mapsUpstreamErrorsAndRetriesOnlyTransientFailures(int upstream, int expectedStatus, String code, int attempts) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(upstream, "{\"status_message\":\"Audit upstream failure\"}", calls);
        try {
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client(server).searchMovies("matrix", 1, "ru-RU"));
            assertThat(error.getStatus().value()).isEqualTo(expectedStatus);
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(calls.get()).isEqualTo(attempts);
        } finally { server.stop(0); }
    }

    @Test
    void malformedJsonReturnsBadGatewayWithoutRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(200, "<html>upstream proxy error</html>", calls);
        try {
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client(server).searchMovies("matrix", 1, "ru-RU"));
            assertThat(error.getStatus().value()).isEqualTo(502);
            assertThat(error.getCode()).isEqualTo("TMDB_BAD_RESPONSE");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void incompleteSuccessPayloadIsRejected() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(200, "{}", calls);
        try {
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client(server).searchMovies("matrix", 1, "ru-RU"));
            assertThat(error.getCode()).isEqualTo("TMDB_BAD_RESPONSE");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test
    void incompleteMovieDetailsAreRejected() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3/movie/603", exchange -> {
            byte[] bytes = "{\"id\":603}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client(server).getMovieDetails(603L, "ru-RU"));
            assertThat(error.getCode()).isEqualTo("TMDB_BAD_RESPONSE");
        } finally { server.stop(0); }
    }

    @Test
    void emptyResponseReturnsBadGateway() throws Exception {
        HttpServer server = server(204, "", new AtomicInteger());
        try {
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client(server).searchMovies("matrix", 1, "ru-RU"));
            assertThat(error.getCode()).isEqualTo("TMDB_BAD_RESPONSE");
        } finally { server.stop(0); }
    }

    @Test
    void missingTokenFailsBeforeSendingRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(200, "{}", calls);
        try {
            TmdbProperties properties = properties(server);
            properties.setAccessToken("");
            TmdbHttpClient client = new TmdbHttpClient(properties, RestClient.builder(), new ObjectMapper());
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client.searchMovies("matrix", 1, "ru-RU"));
            assertThat(error.getCode()).isEqualTo("TMDB_CONFIGURATION_ERROR");
            assertThat(calls.get()).isZero();
        } finally { server.stop(0); }
    }

    @Test
    void upstreamReadTimeoutReturnsBoundedBadGateway() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3/search/movie", exchange -> {
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            TmdbProperties properties = properties(server);
            properties.setRetryMaxAttempts(1);
            properties.setReadTimeout(Duration.ofMillis(150));
            TmdbHttpClient client = new TmdbHttpClient(properties, RestClient.builder(), new ObjectMapper());
            ApiException error = catchThrowableOfType(ApiException.class,
                () -> client.searchMovies("matrix", 1, "ru-RU"));
            assertThat(error.getStatus().value()).isEqualTo(502);
            assertThat(error.getCode()).isEqualTo("TMDB_UNAVAILABLE");
        } finally { release.countDown(); server.stop(0); }
    }

    private static TmdbHttpClient client(HttpServer server) {
        return new TmdbHttpClient(properties(server), RestClient.builder(), new ObjectMapper());
    }

    private static TmdbProperties properties(HttpServer server) {
        TmdbProperties properties = new TmdbProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/3");
        properties.setAccessToken("audit-fake-token");
        properties.setRetryMaxAttempts(2);
        properties.setRetryBackoff(Duration.ZERO);
        return properties;
    }

    private static HttpServer server(int status, String body, AtomicInteger calls) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/3/search/movie", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
