package org.example.echo.tmdb;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Component
@Validated
@ConfigurationProperties(prefix = "app.tmdb")
public class TmdbProperties {
    private static final Logger log = LoggerFactory.getLogger(TmdbProperties.class);
    private static final Duration BLOCKING_RETRY_WARNING_THRESHOLD = Duration.ofSeconds(5);

    @NotBlank
    private String provider = "http";

    @NotBlank
    private String baseUrl = "https://api.themoviedb.org/3";

    private String accessToken = "";

    @NotBlank
    private String language = "ru-RU";

    @NotBlank
    private String fallbackLanguage = "en-US";

    @NotBlank
    private String imageBaseUrl = "https://image.tmdb.org/t/p/w500";

    @Min(1)
    private int castLimit = 15;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(8);

    @Min(1)
    private int retryMaxAttempts = 2;

    @NotNull
    private Duration retryBackoff = Duration.ofMillis(250);

    @NotNull
    private Duration refreshStaleAfter = Duration.ofDays(7);

    @Min(1)
    private long refreshFixedDelayMs = 86_400_000L;

    @Min(1)
    private int refreshBatchSize = 25;

    @PostConstruct
    void validate() {
        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedProvider) && !"stub".equals(normalizedProvider)) {
            throw new IllegalStateException("Invalid app.tmdb.provider");
        }
        if ("http".equals(normalizedProvider) && (accessToken == null || accessToken.isBlank())) {
            throw new IllegalStateException("app.tmdb.access-token must be configured when app.tmdb.provider=http");
        }
        if ("http".equals(normalizedProvider) && hasInvalidHeaderCharacters(accessToken)) {
            throw new IllegalStateException("app.tmdb.access-token contains invalid characters");
        }
        requireValidAbsoluteUrl("app.tmdb.base-url", baseUrl);
        requireValidAbsoluteUrl("app.tmdb.image-base-url", imageBaseUrl);
        warnIfBlockingRetryBudgetIsHigh();
    }

    private boolean hasInvalidHeaderCharacters(String value) {
        return value.chars().anyMatch(character -> character <= 0x20 || character > 0x7E);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFallbackLanguage() {
        return fallbackLanguage;
    }

    public void setFallbackLanguage(String fallbackLanguage) {
        this.fallbackLanguage = fallbackLanguage;
    }

    public String getImageBaseUrl() {
        return imageBaseUrl;
    }

    public void setImageBaseUrl(String imageBaseUrl) {
        this.imageBaseUrl = imageBaseUrl;
    }

    public int getCastLimit() {
        return castLimit;
    }

    public void setCastLimit(int castLimit) {
        this.castLimit = castLimit;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public Duration getRefreshStaleAfter() {
        return refreshStaleAfter;
    }

    public void setRefreshStaleAfter(Duration refreshStaleAfter) {
        this.refreshStaleAfter = refreshStaleAfter;
    }

    public long getRefreshFixedDelayMs() {
        return refreshFixedDelayMs;
    }

    public void setRefreshFixedDelayMs(long refreshFixedDelayMs) {
        this.refreshFixedDelayMs = refreshFixedDelayMs;
    }

    public int getRefreshBatchSize() {
        return refreshBatchSize;
    }

    public void setRefreshBatchSize(int refreshBatchSize) {
        this.refreshBatchSize = refreshBatchSize;
    }

    private void requireValidAbsoluteUrl(String propertyName, String value) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Invalid " + propertyName);
        }
    }

    private void warnIfBlockingRetryBudgetIsHigh() {
        Duration totalBackoff = retryBackoff.multipliedBy(Math.max(retryMaxAttempts - 1L, 0L));
        if (totalBackoff.compareTo(BLOCKING_RETRY_WARNING_THRESHOLD) > 0) {
            log.warn(
                "TMDB retry backoff budget {} exceeds {}; requests are retried synchronously",
                totalBackoff,
                BLOCKING_RETRY_WARNING_THRESHOLD
            );
        }
    }
}
