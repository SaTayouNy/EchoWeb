package org.example.echo.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.example.echo.entity.*;
import org.example.echo.exception.ApiException;
import org.example.echo.model.*;
import org.example.echo.repository.*;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StructuredReviewService {
    private final ReviewRepository reviews;
    private final ReviewLikeRepository likes;
    private final UserRepository users;
    private final StructuredRatingService ratings;
    @PersistenceContext private EntityManager em;

    public StructuredReviewService(ReviewRepository reviews, ReviewLikeRepository likes, UserRepository users, StructuredRatingService ratings) {
        this.reviews = reviews;
        this.likes = likes;
        this.users = users;
        this.ratings = ratings;
    }

    @Transactional
    public ReviewModels.ReviewResponse create(AuthenticatedUserPrincipal actor, ReviewModels.CreateRequest request) {
        UserEntity user = lockUser(actor.userId());
        if (reviews.existsByUserUserIdAndMediaTypeAndTmdbMovieId(actor.userId(), request.mediaType(), request.tmdbId())) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "A review for this release already exists");
        }
        UserRatingEntity rating = requiredRating(actor.userId(), request.mediaType(), request.tmdbId());
        ReviewEntity review = new ReviewEntity();
        review.setUser(user);
        review.setUserRating(rating);
        review.setMedia(rating.getMedia());
        review.setMediaType(request.mediaType());
        review.setTmdbMovieId(request.tmdbId());
        review.setMovieTitle(rating.getTitle() == null ? "Untitled" : rating.getTitle());
        review.setMoviePosterPath(rating.getPosterPath());
        writeText(review, request.title(), request.content(), request.draft());
        return response(reviews.saveAndFlush(review));
    }

    @Transactional
    public ReviewModels.ReviewResponse update(AuthenticatedUserPrincipal actor, UUID id, ReviewModels.UpdateRequest request) {
        ReviewEntity review = lockOwned(actor, id);
        review.setUserRating(requiredRating(actor.userId(), review.getMediaType(), review.getTmdbMovieId()));
        writeText(review, request.title(), request.content(), request.draft());
        return response(reviews.saveAndFlush(review));
    }

    @Transactional
    public void delete(AuthenticatedUserPrincipal actor, UUID id) {
        reviews.delete(lockOwned(actor, id));
        reviews.flush();
    }

    @Transactional
    public ReviewModels.ReviewResponse publish(AuthenticatedUserPrincipal actor, UUID id) {
        requireAdmin(actor);
        ReviewEntity initial = find(id);
        UUID owner = initial.getUser().getUserId();
        lockUser(owner);
        ReviewEntity review = reviews.findByIdForUpdate(id).orElseThrow(StructuredReviewService::notFound);
        em.refresh(review);
        requireStructured(review);
        if (review.getStatus() != ReviewStatus.PENDING_MODERATION) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_NOT_PENDING", "Only a pending review can be published");
        }
        text(review.getTitle(), "title", 200, false, false);
        text(review.getReviewText(), "content", 10000, false, true);
        review.setStatus(ReviewStatus.PUBLISHED);
        review.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return response(reviews.saveAndFlush(review));
    }

    @Transactional(readOnly = true)
    public ReviewModels.ReviewResponse get(AuthenticatedUserPrincipal actor, UUID id) {
        ReviewEntity review = find(id);
        if (review.getStatus() != ReviewStatus.PUBLISHED && (actor == null ||
            (!actor.userId().equals(review.getUser().getUserId()) && actor.role() != Role.ADMIN))) throw notFound();
        return response(review);
    }

    @Transactional(readOnly = true)
    public ReviewModels.ListResponse list(UUID userId, MediaKind kind, Long tmdbId, ReviewStatus status, boolean privateListing,
                                         int limit, int offset, String sort, SortDirectionValue order) {
        if (limit < 1 || limit > 100 || offset < 0) throw invalid("pagination", "Invalid pagination");
        String expression = switch (sort) {
            case "createdAt" -> "r.createdAt";
            case "publishedAt" -> "r.publishedAt";
            case "rating" -> "r.userRating.finalScore";
            case "likes" -> "(select count(l) from ReviewLikeEntity l where l.review = r)";
            default -> throw invalid("sort", "Allowed: createdAt, publishedAt, rating, likes");
        };
        String where = " where 1 = 1";
        if (!privateListing) { status = ReviewStatus.PUBLISHED; }
        if (status != null) where += " and r.status = :status";
        if (userId != null) where += " and r.user.userId = :userId";
        if (kind != null) where += " and r.mediaType = :kind";
        if (tmdbId != null) where += " and r.tmdbMovieId = :tmdbId";
        TypedQuery<ReviewEntity> query = em.createQuery("select r from ReviewEntity r" + where + " order by " + expression
            + (order == SortDirectionValue.ASC ? " asc" : " desc") + ", r.reviewId desc", ReviewEntity.class);
        TypedQuery<Long> count = em.createQuery("select count(r) from ReviewEntity r" + where, Long.class);
        for (TypedQuery<?> q : List.of(query, count)) {
            if (status != null) q.setParameter("status", status);
            if (userId != null) q.setParameter("userId", userId);
            if (kind != null) q.setParameter("kind", kind);
            if (tmdbId != null) q.setParameter("tmdbId", tmdbId);
        }
        List<ReviewModels.ReviewResponse> items = query.setFirstResult(offset).setMaxResults(limit).getResultList().stream().map(this::response).toList();
        return new ReviewModels.ListResponse(items, new ApiModels.PaginationMeta(limit, offset, count.getSingleResult()));
    }

    private ReviewModels.ReviewResponse response(ReviewEntity r) {
        MediaEntity media = r.getMedia();
        return new ReviewModels.ReviewResponse(r.getReviewId(), r.getUser().getUserId(),
            new ReviewModels.MediaResponse(r.getMediaType(), r.getTmdbMovieId(), media == null ? r.getMoviePosterPath() : media.getPosterPath(),
                media == null ? null : media.getPosterUrl(), media == null ? r.getMovieTitle() : media.getTitle(), media == null ? null : media.getDirector()),
            r.getUserRating() == null || r.getUserRating().getSchemeVersion() == null ? null : ratings.toResponse(r.getUserRating()),
            r.getUserRating() == null || r.getUserRating().getSchemeVersion() == null ? r.getRating() : null, r.getTitle(), r.getReviewText(),
            likes.countByReviewReviewId(r.getReviewId()), r.getPublishedAt(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private ReviewEntity lockOwned(AuthenticatedUserPrincipal actor, UUID id) {
        ReviewEntity initial = find(id);
        UUID owner = initial.getUser().getUserId();
        if (!actor.userId().equals(owner)) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the author may change this review");
        lockUser(owner);
        ReviewEntity review = reviews.findByIdForUpdate(id).orElseThrow(StructuredReviewService::notFound);
        em.refresh(review);
        return review;
    }

    private UserEntity lockUser(UUID id) { return users.findByIdForUpdate(id).orElseThrow(StructuredReviewService::notFound); }
    private ReviewEntity find(UUID id) { return reviews.findByReviewId(id).orElseThrow(StructuredReviewService::notFound); }
    private void requireStructured(ReviewEntity review) {
        if (review.getUserRating() == null || review.getUserRating().getSchemeVersion() == null)
            throw new ApiException(HttpStatus.CONFLICT, "STRUCTURED_RATING_REQUIRED", "Save the five rating parameters before submitting this review");
    }

    private UserRatingEntity requiredRating(UUID userId, MediaKind kind, Long tmdbId) {
        try {
            return ratings.requireStructuredRating(userId, kind, tmdbId);
        } catch (ApiException ex) {
            if ("STRUCTURED_RATING_NOT_FOUND".equals(ex.getCode())) {
                throw new ApiException(HttpStatus.CONFLICT, "STRUCTURED_RATING_REQUIRED",
                    "Save the five rating parameters before creating or submitting a review");
            }
            throw ex;
        }
    }

    private void writeText(ReviewEntity r, String title, String content, boolean draft) {
        String normalizedTitle = text(title, "title", 200, draft, false);
        String normalizedContent = text(content, "content", 10000, draft, true);
        ReviewStatus next = draft ? ReviewStatus.NOT_WRITTEN : ReviewStatus.PENDING_MODERATION;
        if (r.getStatus() == ReviewStatus.PUBLISHED && !draft && Objects.equals(r.getTitle(), normalizedTitle)
            && Objects.equals(r.getReviewText(), normalizedContent)) return;
        r.setTitle(normalizedTitle);
        r.setReviewText(normalizedContent);
        r.setStatus(next);
        r.setPublishedAt(null);
    }

    private static String text(String raw, String field, int max, boolean emptyAllowed, boolean multiline) {
        String normalized = raw == null ? "" : raw.strip();
        if ((!emptyAllowed && normalized.isBlank()) || normalized.length() > max)
            throw invalid(field, "Must contain " + (emptyAllowed ? "0" : "1") + " to " + max + " characters");
        if (normalized.codePoints().anyMatch(c -> Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\r' || c == '\t'))))
            throw invalid(field, "Contains invalid control characters");
        return normalized;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message, List.of(new ApiModels.ErrorDetail(field, message)));
    }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"); }
    public static void requireAdmin(AuthenticatedUserPrincipal actor) {
        if (actor == null || actor.role() != Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Administrator permission required");
    }
}
