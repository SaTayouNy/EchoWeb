package org.example.echo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewStatus;
import org.example.echo.model.ReviewModels;
import org.example.echo.model.SortDirectionValue;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.ProfileInteractionService;
import org.example.echo.service.StructuredReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/v2/reviews")
@Tag(name = "Reviews", description = "Reviews linked to five-parameter ratings; publication requires administrator moderation")
public class StructuredReviewController {
    private final StructuredReviewService service;
    private final ProfileInteractionService interactions;

    public StructuredReviewController(StructuredReviewService service, ProfileInteractionService interactions) {
        this.service = service;
        this.interactions = interactions;
    }

    @GetMapping
    @Operation(summary = "List published reviews", description = "Filter by release or author. sort: createdAt, publishedAt, rating, likes. Stable UUID tie-break; limit 1..100.")
    public ReviewModels.ListResponse list(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) MediaKind mediaType,
        @RequestParam(required = false) @Min(1) Long tmdbId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "publishedAt") String sort,
        @RequestParam(defaultValue = "desc") SortDirectionValue order
    ) { return service.list(userId, mediaType, tmdbId, null, false, limit, offset, sort, order); }

    @GetMapping("/mine")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List own reviews including drafts and pending submissions")
    public ReviewModels.ListResponse mine(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @RequestParam(required = false) ReviewStatus status,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue = "0") @Min(0) int offset,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") SortDirectionValue order
    ) { return service.list(actor.userId(), null, null, status, true, limit, offset, sort, order); }

    @GetMapping("/moderation")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Administrator moderation queue")
    public ReviewModels.ListResponse moderation(
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue = "0") @Min(0) int offset
    ) { return service.list(null, null, null, ReviewStatus.PENDING_MODERATION, true, limit, offset, "createdAt", SortDirectionValue.ASC); }

    @PostMapping
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create draft or submit review", description = "Requires the author's saved structured rating for the same release. Final score is calculated by the backend. One review per author and release.")
    public ReviewModels.ReviewResponse create(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @Valid @RequestBody ReviewModels.CreateRequest request) {
        return service.create(actor, request);
    }

    @GetMapping("/{reviewId}")
    @Operation(summary = "Get review", description = "Unpublished reviews are visible only to the author and administrators; otherwise 404.")
    public ReviewModels.ReviewResponse get(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID reviewId) {
        return service.get(actor, reviewId);
    }

    @PutMapping("/{reviewId}")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Replace own review text", description = "Changed published text returns to moderation. draft=true withdraws into NOT_WRITTEN; submitting requires nonblank title and content.")
    public ReviewModels.ReviewResponse update(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID reviewId, @Valid @RequestBody ReviewModels.UpdateRequest request) {
        return service.update(actor, reviewId, request);
    }

    @DeleteMapping("/{reviewId}")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete own review and its likes/answers", description = "The rating remains saved; the author can create a new review afterwards.")
    public void delete(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID reviewId) { service.delete(actor, reviewId); }

    @PostMapping("/{reviewId}/publish")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Administrator publishes a pending review")
    public ReviewModels.ReviewResponse publish(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID reviewId) { return service.publish(actor, reviewId); }

    @PutMapping("/{reviewId}/like")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Like published review idempotently")
    public void like(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID reviewId) { interactions.likeReview(actor, reviewId); }

    @DeleteMapping("/{reviewId}/like")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove own like idempotently")
    public void unlike(@AuthenticationPrincipal AuthenticatedUserPrincipal actor, @PathVariable UUID reviewId) { interactions.unlikeReview(actor, reviewId); }
}
