package org.example.echo.service;

import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.MediaSyncStatus;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.UserEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.model.ApiModels;
import org.example.echo.model.ReviewSortField;
import org.example.echo.model.SortDirectionValue;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.util.StringNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final AuthService authService;
    private final TmdbMediaService tmdbMediaService;
    private final TmdbBackgroundSyncService tmdbBackgroundSyncService;
    private final UserRatingService userRatingService;
    private final StructuredReviewService structuredReviewService;

    public ReviewService(
        ReviewRepository reviewRepository,
        AuthService authService,
        TmdbMediaService tmdbMediaService,
        TmdbBackgroundSyncService tmdbBackgroundSyncService,
        UserRatingService userRatingService,
        StructuredReviewService structuredReviewService
    ) {
        this.reviewRepository = reviewRepository;
        this.authService = authService;
        this.tmdbMediaService = tmdbMediaService;
        this.tmdbBackgroundSyncService = tmdbBackgroundSyncService;
        this.userRatingService = userRatingService;
        this.structuredReviewService = structuredReviewService;
    }

    @Transactional(readOnly = true)
    public ApiModels.ListReviewsResponse listReviews(
        Integer limit,
        Integer offset,
        Long tmdbMovieId,
        MediaKind mediaType,
        UUID userId,
        ReviewSortField sort,
        SortDirectionValue order
    ) {
        List<ReviewEntity> reviews = reviewRepository.findFiltered(
            tmdbMovieId,
            mediaType,
            userId,
            sort,
            order,
            limit,
            offset
        );
        long total = reviewRepository.countFiltered(tmdbMovieId, mediaType, userId);

        return new ApiModels.ListReviewsResponse(
            reviews.stream().map(ApiModels::toReviewResponse).toList(),
            new ApiModels.PaginationMeta(limit, offset, total)
        );
    }

    @Transactional
    public ApiModels.ReviewResponse createReview(AuthenticatedUserPrincipal actor, ApiModels.CreateReviewRequest request) {
        throw legacyWriteDisabled();
    }

    @Transactional(readOnly = true)
    public ApiModels.ReviewResponse getReview(UUID reviewId) {
        ReviewEntity review = findReview(reviewId);
        if (review.getStatus() != org.example.echo.entity.ReviewStatus.PUBLISHED) throw reviewNotFound();
        return ApiModels.toReviewResponse(review);
    }

    @Transactional
    public ApiModels.ReviewResponse updateReview(
        AuthenticatedUserPrincipal actor,
        UUID reviewId,
        ApiModels.UpdateReviewRequest request
    ) {
        ReviewEntity review = findReview(reviewId);
        authService.checkOwnership(actor, review.getUser().getUserId());
        throw legacyWriteDisabled();
    }

    @Transactional
    public void deleteReview(AuthenticatedUserPrincipal actor, UUID reviewId) {
        structuredReviewService.delete(actor, reviewId);
    }

    private ReviewEntity findReview(UUID reviewId) {
        return reviewRepository.findByReviewId(reviewId)
            .orElseThrow(this::reviewNotFound);
    }

    private ApiException legacyWriteDisabled() {
        return new ApiException(HttpStatus.CONFLICT, "LEGACY_REVIEW_WRITE_DISABLED", "Use /v2/reviews with a saved structured rating and moderation");
    }

    private ApiException reviewNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Resource already exists");
    }

    private String defaultIfBlank(String... values) {
        String normalized = StringNormalizer.firstNonBlank(values);
        return normalized == null ? "Untitled" : normalized;
    }
}
