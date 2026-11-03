package org.example.echo.service;

import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.UserEntity;
import org.example.echo.entity.UserRatingEntity;
import org.example.echo.model.ApiModels;
import org.example.echo.repository.UserRatingRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.exception.ApiException;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.util.StringNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class UserRatingService {
    private final UserRatingRepository userRatingRepository;
    private final AuthService authService;
    private final TmdbMediaService tmdbMediaService;
    private final UserRepository userRepository;
    private final ReviewRepository reviews;

    public UserRatingService(
        UserRatingRepository userRatingRepository,
        AuthService authService,
        TmdbMediaService tmdbMediaService,
        UserRepository userRepository,
        ReviewRepository reviews
    ) {
        this.userRatingRepository = userRatingRepository;
        this.authService = authService;
        this.tmdbMediaService = tmdbMediaService;
        this.userRepository = userRepository;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public ApiModels.ListUserRatingsResponse list(UUID userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
        }
        Page<UserRatingEntity> ratings = userRatingRepository.findByUserUserId(
            userId,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("ratingId")))
        );
        return new ApiModels.ListUserRatingsResponse(
            ratings.getContent().stream().map(ApiModels::toUserRatingResponse).toList(),
            new ApiModels.PaginationMeta(size, page * size, ratings.getTotalElements())
        );
    }

    @Transactional
    public ApiModels.UserRatingResponse upsert(
        AuthenticatedUserPrincipal actor,
        MediaKind mediaType,
        Long tmdbId,
        ApiModels.UpsertUserRatingRequest request
    ) {
        UserEntity user = lockUser(actor.userId());
        MediaEntity media = tmdbMediaService.findOrCreatePlaceholder(
            mediaType,
            tmdbId,
            StringNormalizer.trim(request.title()),
            request.releaseDate(),
            StringNormalizer.trim(request.posterPath())
        );
        UserRatingEntity rating = saveRating(user, media, request.rating());
        return ApiModels.toUserRatingResponse(rating);
    }

    @Transactional
    public void delete(AuthenticatedUserPrincipal actor, MediaKind mediaType, Long tmdbId) {
        UserEntity user = lockUser(actor.userId());
        userRatingRepository.findByUserUserIdAndMediaTypeAndTmdbId(user.getUserId(), mediaType, tmdbId)
            .ifPresent(rating -> {
                requireLegacy(rating);
                if (reviews.existsByUserRatingRatingId(rating.getRatingId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "RATING_IN_USE", "Delete the linked review before deleting its rating");
                }
                userRatingRepository.delete(rating);
            });
    }

    @Transactional
    public UserRatingEntity recordFromReview(UserEntity user, MediaEntity media, Integer value) {
        lockUser(user.getUserId());
        return saveRating(user, media, value);
    }

    private UserRatingEntity saveRating(UserEntity user, MediaEntity media, Integer value) {
        UserRatingEntity rating = userRatingRepository
            .findByUserUserIdAndMediaTypeAndTmdbId(user.getUserId(), media.getMediaType(), media.getTmdbId())
            .orElseGet(UserRatingEntity::new);
        requireLegacy(rating);
        rating.setUser(user);
        rating.setMedia(media);
        rating.setMediaType(media.getMediaType());
        rating.setTmdbId(media.getTmdbId());
        rating.setTitle(media.getTitle());
        rating.setPosterPath(media.getPosterPath());
        rating.setRating(value);
        return userRatingRepository.saveAndFlush(rating);
    }

    private UserEntity lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    }

    private void requireLegacy(UserRatingEntity rating) {
        if (rating.getSchemeVersion() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "STRUCTURED_RATING_REQUIRES_V2", "Use the v2 parameter rating API");
        }
    }
}
