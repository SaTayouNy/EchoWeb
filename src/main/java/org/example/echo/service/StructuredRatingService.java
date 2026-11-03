package org.example.echo.service;

import org.example.echo.entity.*;
import org.example.echo.exception.ApiException;
import org.example.echo.model.RatingModels;
import org.example.echo.repository.*;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class StructuredRatingService {
    private final UserRatingRepository ratings;
    private final UserRepository users;
    private final RatingSchemeRepository schemes;
    private final RatingParameterRepository parameters;
    private final ReviewRepository reviews;
    private final TmdbMediaService mediaService;
    private final RatingCalculator calculator;
    private final TransactionTemplate transactions;

    public StructuredRatingService(UserRatingRepository ratings, UserRepository users, RatingSchemeRepository schemes,
                                   RatingParameterRepository parameters, ReviewRepository reviews,
                                   TmdbMediaService mediaService, RatingCalculator calculator,
                                   PlatformTransactionManager transactionManager) {
        this.ratings = ratings;
        this.users = users;
        this.schemes = schemes;
        this.parameters = parameters;
        this.reviews = reviews;
        this.mediaService = mediaService;
        this.calculator = calculator;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public RatingModels.SchemeResponse currentScheme() {
        List<RatingSchemeEntity> active = schemes.findByActiveTrue();
        if (active.size() != 1) throw error(HttpStatus.SERVICE_UNAVAILABLE, "RATING_SCHEME_NOT_CONFIGURED", "Exactly one active rating scheme is required");
        return schemeResponse(active.getFirst());
    }

    @Transactional(readOnly = true)
    public RatingModels.SchemeResponse scheme(String version) { return schemeResponse(requireScheme(version)); }

    public RatingModels.RatingResponse upsert(AuthenticatedUserPrincipal actor, MediaKind kind, Long tmdbId,
                                             RatingModels.UpsertRatingRequest request) {
        // Reject invalid scores before resolving external media. Network calls never hold account locks.
        RatingSchemeEntity scheme = requireScheme(request.schemeVersion());
        List<RatingParameterEntity> definitions = parameters.findBySchemeVersionOrderByDisplayOrder(scheme.getVersion());
        RatingModels.Calculation calculation = calculator.calculate(scheme, definitions, request.parameters());
        MediaEntity media = mediaService.getOrSync(kind, tmdbId);
        return transactions.execute(status -> {
            UserEntity user = lockUser(actor.userId());
            UserRatingEntity rating = ratings.findByUserUserIdAndMediaTypeAndTmdbId(actor.userId(), kind, tmdbId)
                .orElseGet(UserRatingEntity::new);
            if (rating.getSchemeVersion() != null && !rating.getSchemeVersion().equals(scheme.getVersion())) {
                throw error(HttpStatus.CONFLICT, "RATING_SCHEME_CONFLICT", "Existing ratings retain their original scheme; use that version to edit");
            }
            if (rating.getSchemeVersion() == null && !scheme.isActive()) {
                throw error(HttpStatus.CONFLICT, "RATING_SCHEME_INACTIVE", "New ratings require the current active scheme");
            }
            boolean changed = !scheme.getVersion().equals(rating.getSchemeVersion())
                || !request.parameters().equals(rating.getParameterValues());
            if (!changed) return toResponse(rating);
            rating.setUser(user);
            rating.setMedia(media);
            rating.setMediaType(kind);
            rating.setTmdbId(tmdbId);
            rating.setTitle(media.getTitle());
            rating.setPosterPath(media.getPosterPath());
            rating.setSchemeVersion(scheme.getVersion());
            rating.setParameterValues(request.parameters());
            rating.setFinalScore(calculation.finalScore());
            rating.touch();
            ratings.saveAndFlush(rating);
            // A published review embeds this rating, so changed scores must be moderated again.
            reviews.requeuePublishedByRatingId(rating.getRatingId(), java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            return toResponse(rating);
        });
    }

    @Transactional(readOnly = true)
    public RatingModels.RatingResponse get(UUID userId, MediaKind kind, Long tmdbId) {
        return toResponse(findStructured(userId, kind, tmdbId));
    }

    @Transactional(readOnly = true)
    public RatingModels.ListRatingsResponse list(UUID userId, int page, int size) {
        if (!users.existsById(userId)) throw error(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        var result = ratings.findByUserUserIdAndSchemeVersionIsNotNull(userId,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("ratingId"))));
        return new RatingModels.ListRatingsResponse(result.stream().map(this::toResponse).toList(),
            new RatingModels.PageMeta(page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public RatingModels.MovieRatingsResponse aggregate(MediaKind kind, Long tmdbId) {
        Map<String, Map<String, BigDecimal>> averages = new HashMap<>();
        for (var value : ratings.aggregateParameters(kind, tmdbId)) {
            averages.computeIfAbsent(value.getSchemeVersion(), ignored -> new LinkedHashMap<>())
                .put(value.getParameterCode(), average(value.getAverageValue()));
        }
        List<RatingModels.SchemeAggregate> result = ratings.aggregateScores(kind, tmdbId).stream()
            .map(row -> new RatingModels.SchemeAggregate(row.getSchemeVersion(), row.getRatingCount(),
                averages.getOrDefault(row.getSchemeVersion(), Map.of()), average(row.getAverageFinalScore())))
            .toList();
        return new RatingModels.MovieRatingsResponse(kind, tmdbId,
            result.stream().mapToLong(RatingModels.SchemeAggregate::count).sum(), result);
    }

    @Transactional
    public void delete(AuthenticatedUserPrincipal actor, MediaKind kind, Long tmdbId) {
        lockUser(actor.userId());
        ratings.findByUserUserIdAndMediaTypeAndTmdbId(actor.userId(), kind, tmdbId)
            .filter(r -> r.getSchemeVersion() != null).ifPresent(rating -> {
                if (reviews.existsByUserRatingRatingId(rating.getRatingId())) {
                    throw error(HttpStatus.CONFLICT, "RATING_IN_USE", "Delete the linked review before deleting its rating");
                }
                ratings.delete(rating);
            });
    }

    /** Review mutations join the caller's transaction and use the same account lock order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserRatingEntity requireStructuredRating(UUID userId, MediaKind kind, Long tmdbId) {
        lockUser(userId);
        return findStructured(userId, kind, tmdbId);
    }

    @Transactional(readOnly = true)
    public RatingModels.RatingResponse toResponse(UserRatingEntity rating) {
        var scheme = requireScheme(rating.getSchemeVersion());
        var definitions = parameters.findBySchemeVersionOrderByDisplayOrder(scheme.getVersion());
        var calculated = calculator.calculate(scheme, definitions, rating.getParameterValues());
        Map<String, Integer> ordered = new LinkedHashMap<>();
        definitions.forEach(p -> ordered.put(p.getCode(), rating.getParameterValues().get(p.getCode())));
        return new RatingModels.RatingResponse(rating.getRatingId(), rating.getUser().getUserId(), rating.getMediaType(),
            rating.getTmdbId(), rating.getTitle(), rating.getPosterPath(), rating.getSchemeVersion(), ordered,
            calculated.formQuality(), calculated.baseScore(), calculated.impressionMultiplier(), calculated.finalScore(),
            rating.getCreatedAt(), rating.getUpdatedAt());
    }

    private UserRatingEntity findStructured(UUID userId, MediaKind kind, Long tmdbId) {
        return ratings.findByUserUserIdAndMediaTypeAndTmdbId(userId, kind, tmdbId)
            .filter(r -> r.getSchemeVersion() != null)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STRUCTURED_RATING_NOT_FOUND", "Create a parameter-based rating first"));
    }

    private UserEntity lockUser(UUID userId) {
        return users.findByIdForUpdate(userId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    }

    private RatingSchemeEntity requireScheme(String version) {
        return schemes.findById(version).orElseThrow(() -> error(HttpStatus.UNPROCESSABLE_ENTITY, "RATING_SCHEME_NOT_FOUND", "Rating scheme not found"));
    }

    private RatingModels.SchemeResponse schemeResponse(RatingSchemeEntity scheme) {
        return new RatingModels.SchemeResponse(scheme.getVersion(), scheme.getFormulaCode(), scheme.isActive(),
            scheme.getMinimumMultiplier(), scheme.getMaximumMultiplier(), scheme.getRoundingMode(),
            parameters.findBySchemeVersionOrderByDisplayOrder(scheme.getVersion()).stream()
                .map(p -> new RatingModels.ParameterDefinition(p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getRole(), p.getDisplayOrder(), p.getMinimumValue(), p.getMaximumValue(), 1)).toList());
    }

    private BigDecimal average(Double value) { return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros(); }
    private ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
