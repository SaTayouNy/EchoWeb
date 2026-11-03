package org.example.echo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.echo.entity.MediaKind;
import org.example.echo.model.ApiModels;
import org.example.echo.model.ReviewSortField;
import org.example.echo.model.SortDirectionValue;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.ReviewService;
import org.example.echo.service.ProfileInteractionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/v1/reviews")
public class ReviewController {
    private final ReviewService reviewService;
    private final ProfileInteractionService profileInteractionService;

    public ReviewController(ReviewService reviewService, ProfileInteractionService profileInteractionService) {
        this.reviewService = reviewService;
        this.profileInteractionService = profileInteractionService;
    }

    @GetMapping
    public ApiModels.ListReviewsResponse listReviews(
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit,
        @RequestParam(defaultValue = "0") @Min(0) Integer offset,
        @RequestParam(required = false) @Min(1) Long tmdbMovieId,
        @RequestParam(required = false) MediaKind mediaType,
        @RequestParam(required = false) UUID userId,
        @RequestParam(defaultValue = "createdAt") ReviewSortField sort,
        @RequestParam(defaultValue = "desc") SortDirectionValue order
    ) {
        return reviewService.listReviews(limit, offset, tmdbMovieId, mediaType, userId, sort, order);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.ReviewResponse createReview(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @Valid @RequestBody ApiModels.CreateReviewRequest request
    ) {
        return reviewService.createReview(actor, request);
    }

    @GetMapping("/{reviewId}")
    public ApiModels.ReviewResponse getReview(@PathVariable UUID reviewId) {
        return reviewService.getReview(reviewId);
    }

    @PatchMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ApiModels.ReviewResponse updateReview(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID reviewId,
        @Valid @RequestBody ApiModels.UpdateReviewRequest request
    ) {
        return reviewService.updateReview(actor, reviewId, request);
    }

    @DeleteMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID reviewId
    ) {
        reviewService.deleteReview(actor, reviewId);
    }

    @PutMapping("/{reviewId}/like")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void likeReview(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID reviewId
    ) {
        profileInteractionService.likeReview(actor, reviewId);
    }

    @DeleteMapping("/{reviewId}/like")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlikeReview(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID reviewId
    ) {
        profileInteractionService.unlikeReview(actor, reviewId);
    }
}
