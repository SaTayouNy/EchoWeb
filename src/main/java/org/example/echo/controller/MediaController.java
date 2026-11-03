package org.example.echo.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.echo.entity.MediaKind;
import org.example.echo.model.ApiModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.TmdbMediaService;
import org.example.echo.tmdb.TmdbTrendingWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/media")
public class MediaController {
    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final TmdbMediaService tmdbMediaService;

    public MediaController(TmdbMediaService tmdbMediaService) {
        this.tmdbMediaService = tmdbMediaService;
    }

    @GetMapping("/{mediaType}/{tmdbId}")
    public ApiModels.MediaResponse getMedia(
        @PathVariable MediaKind mediaType,
        @PathVariable @Min(1) Long tmdbId
    ) {
        return tmdbMediaService.getMedia(mediaType, tmdbId);
    }

    @PostMapping("/{mediaType}/{tmdbId}/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiModels.MediaResponse syncMedia(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable MediaKind mediaType,
        @PathVariable @Min(1) Long tmdbId
    ) {
        log.info("Manual TMDB sync requested: mediaType={}, tmdbId={}, byUserId={}", mediaType, tmdbId, actor.userId());
        return ApiModels.toMediaResponse(tmdbMediaService.syncMedia(mediaType, tmdbId));
    }

    @GetMapping("/search/movies")
    public ApiModels.MediaSearchResponse searchMovies(
        @RequestParam @NotBlank @Size(max = 200) String query,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.searchMovies(query, page);
    }

    @GetMapping("/search/tv")
    public ApiModels.MediaSearchResponse searchTv(
        @RequestParam @NotBlank @Size(max = 200) String query,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.searchTv(query, page);
    }

    @GetMapping("/discover/movies/popular")
    public ApiModels.MediaSearchResponse getPopularMovies(
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.getPopularMovies(page);
    }

    @GetMapping("/discover/tv/popular")
    public ApiModels.MediaSearchResponse getPopularTv(
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.getPopularTv(page);
    }

    @GetMapping("/discover/movies/trending")
    public ApiModels.MediaSearchResponse getTrendingMovies(
        @RequestParam(defaultValue = "week") TmdbTrendingWindow window,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.getTrendingMovies(window, page);
    }

    @GetMapping("/discover/tv/trending")
    public ApiModels.MediaSearchResponse getTrendingTv(
        @RequestParam(defaultValue = "week") TmdbTrendingWindow window,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.getTrendingTv(window, page);
    }

    @GetMapping("/movie/{tmdbId}/recommendations")
    public ApiModels.MediaSearchResponse getMovieRecommendations(
        @PathVariable @Min(1) Long tmdbId,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.getMovieRecommendations(tmdbId, page);
    }

    @GetMapping("/tv/{tmdbId}/recommendations")
    public ApiModels.MediaSearchResponse getTvRecommendations(
        @PathVariable @Min(1) Long tmdbId,
        @RequestParam(defaultValue = "1") @Min(1) @Max(1000) Integer page
    ) {
        return tmdbMediaService.getTvRecommendations(tmdbId, page);
    }
}
