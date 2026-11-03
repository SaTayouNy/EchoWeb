package org.example.echo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.example.echo.entity.MediaKind;
import org.example.echo.model.RatingModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.StructuredRatingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Validated @RestController @RequestMapping("/v2")
@Tag(name = "Structured ratings", description = "Versioned five-parameter ratings on the 100-point scale")
public class StructuredRatingController {
    private final StructuredRatingService service;
    public StructuredRatingController(StructuredRatingService service) { this.service = service; }

    @GetMapping("/rating-schemes/current")
    @Operation(summary = "Current rating parameters and formula metadata for onboarding")
    public RatingModels.SchemeResponse current() { return service.currentScheme(); }

    @GetMapping("/rating-schemes/{version}")
    public RatingModels.SchemeResponse scheme(@PathVariable @Size(max = 64) String version) { return service.scheme(version); }

    @PutMapping("/me/ratings/{mediaType}/{tmdbId}")
    @PreAuthorize("isAuthenticated()") @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create or replace own parameter scores", description = "Final score is calculated on the server. Existing ratings retain their scheme version. Changed scores requeue published reviews for moderation.")
    public RatingModels.RatingResponse upsert(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable MediaKind mediaType, @PathVariable @Min(1) Long tmdbId,
        @Valid @RequestBody RatingModels.UpsertRatingRequest request) {
        return service.upsert(actor, mediaType, tmdbId, request);
    }

    @GetMapping("/me/ratings/{mediaType}/{tmdbId}")
    @PreAuthorize("isAuthenticated()") @SecurityRequirement(name = "bearerAuth")
    public RatingModels.RatingResponse get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable MediaKind mediaType, @PathVariable @Min(1) Long tmdbId) {
        return service.get(actor.userId(), mediaType, tmdbId);
    }

    @DeleteMapping("/me/ratings/{mediaType}/{tmdbId}")
    @PreAuthorize("isAuthenticated()") @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own rating", description = "Returns 409 RATING_IN_USE if a review references this rating.")
    public void delete(@AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable MediaKind mediaType, @PathVariable @Min(1) Long tmdbId) { service.delete(actor, mediaType, tmdbId); }

    @GetMapping("/users/{userId}/ratings")
    public RatingModels.ListRatingsResponse list(@PathVariable UUID userId,
        @RequestParam(defaultValue = "0") @Min(0) Integer page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) { return service.list(userId, page, size); }

    @GetMapping("/media/{mediaType}/{tmdbId}/ratings")
    @Operation(summary = "Aggregate parameter and final scores by scheme", description = "Average final score is the mean of individual rounded scores. Schemes and legacy scores are never mixed. Empty release returns count=0.")
    public RatingModels.MovieRatingsResponse aggregate(@PathVariable MediaKind mediaType,
        @PathVariable @Min(1) Long tmdbId) { return service.aggregate(mediaType, tmdbId); }
}
