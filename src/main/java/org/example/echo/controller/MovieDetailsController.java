package org.example.echo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.example.echo.entity.MediaKind;
import org.example.echo.model.ApiModels;
import org.example.echo.model.MovieDetailsResponse;
import org.example.echo.service.StructuredRatingService;
import org.example.echo.service.TmdbMediaService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v2/media/movie")
@Tag(name = "Movie details", description = "Movie page data and community ratings in one request")
public class MovieDetailsController {
    private final TmdbMediaService mediaService;
    private final StructuredRatingService ratingService;

    public MovieDetailsController(TmdbMediaService mediaService, StructuredRatingService ratingService) {
        this.mediaService = mediaService;
        this.ratingService = ratingService;
    }

    @GetMapping(value = "/{tmdbId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a movie card with community ratings",
        description = "Public endpoint. Movie metadata comes from the existing database cache and is synced from TMDB on a cache miss. "
            + "Ratings come from saved five-parameter user scores. Scheme versions are kept separate; "
            + "averageFinalScore is the average of individual rounded final scores, not a calculation from average parameters.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Movie and rating aggregates", content = @Content(schema = @Schema(implementation = MovieDetailsResponse.class))),
        @ApiResponse(responseCode = "400", description = "TMDB ID is not a number", content = @Content(schema = @Schema(implementation = ApiModels.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Movie not found", content = @Content(schema = @Schema(implementation = ApiModels.ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Invalid TMDB ID", content = @Content(schema = @Schema(implementation = ApiModels.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "TMDB is unavailable or returned invalid data on a cache miss", content = @Content(schema = @Schema(implementation = ApiModels.ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "TMDB rate limit reached on a cache miss", content = @Content(schema = @Schema(implementation = ApiModels.ErrorResponse.class)))
    })
    public MovieDetailsResponse getMovieDetails(@PathVariable @Min(1) Long tmdbId) {
        var movie = mediaService.getMedia(MediaKind.MOVIE, tmdbId);
        var ratings = ratingService.aggregate(MediaKind.MOVIE, tmdbId);
        return new MovieDetailsResponse(movie, ratings);
    }
}
