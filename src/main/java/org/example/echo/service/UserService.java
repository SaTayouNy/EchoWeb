package org.example.echo.service;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.UserEntity;
import org.example.echo.entity.UserRatingEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.model.ApiModels;
import org.example.echo.model.ReviewSortField;
import org.example.echo.model.SortDirectionValue;
import org.example.echo.repository.AnswerRepository;
import org.example.echo.repository.ReviewLikeRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.repository.UserRatingRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.util.StringNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final UserRatingRepository userRatingRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final AnswerRepository answerRepository;

    public UserService(
        AuthService authService,
        UserRepository userRepository,
        ReviewRepository reviewRepository,
        UserRatingRepository userRatingRepository,
        ReviewLikeRepository reviewLikeRepository,
        AnswerRepository answerRepository
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.userRatingRepository = userRatingRepository;
        this.reviewLikeRepository = reviewLikeRepository;
        this.answerRepository = answerRepository;
    }

    @Transactional(readOnly = true)
    public ApiModels.UserResponse getCurrentUser(AuthenticatedUserPrincipal actor) {
        return ApiModels.toUserResponse(authService.requireUser(actor.userId()));
    }

    @Transactional(readOnly = true)
    public ApiModels.PublicUserResponse getUser(UUID userId) {
        return ApiModels.toPublicUserResponse(findUser(userId));
    }

    @Transactional(readOnly = true)
    public ApiModels.UserProfileResponse getCurrentProfile(AuthenticatedUserPrincipal actor, int activityLimit) {
        return buildProfile(authService.requireUser(actor.userId()), true, activityLimit);
    }

    @Transactional(readOnly = true)
    public ApiModels.UserProfileResponse getProfile(UUID userId, int activityLimit) {
        return buildProfile(findUser(userId), false, activityLimit);
    }

    @Transactional
    public ApiModels.UserProfileResponse updateProfile(
        AuthenticatedUserPrincipal actor,
        ApiModels.UpdateUserProfileRequest request,
        int activityLimit
    ) {
        UserEntity user = authService.requireUser(actor.userId());
        String username = StringNormalizer.trim(request.username());
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        if (userRepository.findByUsernameNormalizedAndUserIdNot(normalizedUsername, user.getUserId()).isPresent()) {
            throw usernameTaken();
        }

        user.setUsername(username);
        user.setAvatarUrl(nullIfBlank(request.avatarUrl()));
        user.setBio(nullIfBlank(request.bio()));
        user.setPreferredGenres(normalizePreferences(request.preferences().genres()));
        user.setPreferredCountries(normalizePreferences(request.preferences().countries()));
        user.setPreferredMediaTypes(normalizeMediaTypes(request.preferences().mediaTypes()));

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw usernameTaken();
        }
        return buildProfile(user, true, activityLimit);
    }

    private ApiModels.UserProfileResponse buildProfile(UserEntity user, boolean includePrivateData, int activityLimit) {
        UUID userId = user.getUserId();
        List<ReviewEntity> reviews = reviewRepository.findFiltered(
            null,
            null,
            userId,
            ReviewSortField.CREATED_AT,
            SortDirectionValue.DESC,
            activityLimit,
            0
        );
        List<UserRatingEntity> ratings = userRatingRepository.findByUserUserId(
            userId,
            PageRequest.of(0, activityLimit, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("ratingId")))
        ).getContent();

        ApiModels.UserProfileStatsResponse statistics = new ApiModels.UserProfileStatsResponse(
            reviewRepository.countByUserUserIdAndStatus(userId, org.example.echo.entity.ReviewStatus.PUBLISHED),
            userRatingRepository.countByUserUserId(userId),
            reviewLikeRepository.countByReviewUserUserId(userId),
            reviewLikeRepository.countByUserUserId(userId),
            answerRepository.countByUserUserIdAndAuthorLikedTrue(userId)
        );

        return new ApiModels.UserProfileResponse(
            userId,
            includePrivateData ? user.getEmail() : null,
            user.getUsername(),
            user.getAvatarUrl(),
            user.getBio(),
            statistics,
            toPreferences(user),
            ratings.stream().map(ApiModels::toUserRatingResponse).toList(),
            reviews.stream().map(ApiModels::toReviewResponse).toList(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private ApiModels.UserPreferencesResponse toPreferences(UserEntity user) {
        List<MediaKind> mediaTypes = new ArrayList<>();
        for (String value : user.getPreferredMediaTypes()) {
            try {
                mediaTypes.add(MediaKind.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // Ignore unsupported legacy values rather than breaking the profile response.
            }
        }
        return new ApiModels.UserPreferencesResponse(
            user.getPreferredGenres(),
            user.getPreferredCountries(),
            List.copyOf(mediaTypes)
        );
    }

    private List<String> normalizePreferences(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = StringNormalizer.trim(value);
            unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        return List.copyOf(unique.values());
    }

    private List<String> normalizeMediaTypes(List<MediaKind> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().distinct().map(Enum::name).toList();
    }

    private String nullIfBlank(String value) {
        String normalized = StringNormalizer.trim(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private UserEntity findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(this::userNotFound);
    }

    private ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    private ApiException usernameTaken() {
        return new ApiException(
            HttpStatus.CONFLICT,
            "USERNAME_TAKEN",
            "Username is already taken",
            List.of(new ApiModels.ErrorDetail("username", "is already taken"))
        );
    }
}
