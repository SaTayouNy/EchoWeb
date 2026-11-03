package org.example.echo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import org.example.echo.validation.Utf8Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.echo.entity.AnswerEntity;
import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaCredit;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.UserEntity;
import org.example.echo.entity.UserRatingEntity;
import org.example.echo.tmdb.MediaSyncData;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ApiModels {
    private static final String MUST_NOT_BE_BLANK = "must not be blank";
    private static final String MUST_NOT_CONTAIN_CONTROL_CHARACTERS = "must not contain control characters";
    private static final int MAX_MOVIE_TITLE_LENGTH = 255;
    private static final int MAX_POSTER_PATH_LENGTH = 255;
    private static final int MAX_REVIEW_TEXT_LENGTH = 10000;
    private static final int MAX_ANSWER_TEXT_LENGTH = 5000;
    private static final int MAX_BIO_LENGTH = 1000;
    private static final int MAX_AVATAR_URL_LENGTH = 1024;
    private static final int MAX_PREFERENCE_LENGTH = 80;

    public record RegisterRequest(
        @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = MUST_NOT_CONTAIN_CONTROL_CHARACTERS) String username,
        @NotBlank @Size(min = 8) @Utf8Size String password
    ) {
    }

    public record LoginRequest(
        @Email @Size(max = 255) String email,
        @Size(max = 80) @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = MUST_NOT_CONTAIN_CONTROL_CHARACTERS) String username,
        @NotBlank @Utf8Size String password
    ) {
        @AssertTrue(message = "username or email is required")
        public boolean isIdentifierProvided() {
            return (username != null && !username.isBlank()) || (email != null && !email.isBlank());
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record EmailVerificationRequest(@NotBlank @Email String email) {
    }

    public record EmailVerificationConfirmRequest(@NotBlank String token) {
    }

    public record PasswordRecoveryRequest(@NotBlank @Email String email) {
    }

    public record PasswordRecoveryConfirmRequest(@NotBlank String token, @NotBlank @Size(min = 8) @Utf8Size String newPassword) {
    }

    public record AuthTokensResponse(String tokenType, String accessToken, String refreshToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegisterResponse(UserResponse user, AuthTokensResponse tokens, Boolean emailVerificationRequired) {
    }

    public record UserResponse(
        UUID userId,
        String email,
        String username,
        String avatarUrl,
        String bio,
        String role,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record PublicUserResponse(UUID userId, String username, String avatarUrl, String bio) {
    }

    public record UserPreferencesResponse(
        List<String> genres,
        List<String> countries,
        List<MediaKind> mediaTypes
    ) {
    }

    public record UserProfileStatsResponse(
        Long reviews,
        Long ratings,
        Long receivedLikes,
        Long givenLikes,
        Long authorLikesReceived
    ) {
    }

    public record UserRatingResponse(
        UUID ratingId,
        UUID userId,
        UUID mediaId,
        MediaKind mediaType,
        Long tmdbId,
        String title,
        String posterPath,
        String posterUrl,
        Integer rating,
        String schemeVersion,
        Integer finalScore,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserProfileResponse(
        UUID userId,
        String email,
        String username,
        String avatarUrl,
        String bio,
        UserProfileStatsResponse statistics,
        UserPreferencesResponse preferences,
        List<UserRatingResponse> ratings,
        List<ReviewResponse> reviews,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record UpdateUserPreferencesRequest(
        @Size(max = 50) List<@NotBlank @Size(max = MAX_PREFERENCE_LENGTH) String> genres,
        @Size(max = 50) List<@NotBlank @Size(max = MAX_PREFERENCE_LENGTH) String> countries,
        @Size(max = 2) List<@NotNull MediaKind> mediaTypes
    ) {
    }

    public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = MUST_NOT_CONTAIN_CONTROL_CHARACTERS) String username,
        @Size(max = MAX_AVATAR_URL_LENGTH) @Pattern(regexp = "^https?://\\S+$", message = "must be an HTTP(S) URL") String avatarUrl,
        @Size(max = MAX_BIO_LENGTH) String bio,
        @NotNull @Valid UpdateUserPreferencesRequest preferences
    ) {
    }

    public record UpsertUserRatingRequest(
        @NotNull @Min(1) @Max(10) Integer rating,
        @Size(max = MAX_MOVIE_TITLE_LENGTH) String title,
        LocalDate releaseDate,
        @Size(max = MAX_POSTER_PATH_LENGTH) String posterPath
    ) {
    }

    public record ListUserRatingsResponse(List<UserRatingResponse> items, PaginationMeta meta) {
    }

    public record ReviewResponse(
        UUID reviewId,
        UUID userId,
        UUID mediaId,
        MediaKind mediaType,
        Long tmdbMovieId,
        String movieTitle,
        LocalDate movieReleaseDate,
        String moviePosterPath,
        Integer rating,
        String reviewText,
        UUID userRatingId,
        String schemeVersion,
        Integer finalScore,
        String title,
        String status,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record AnswerResponse(
        UUID answerId,
        UUID reviewId,
        UUID userId,
        String answerText,
        Boolean authorLiked,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record PaginationMeta(Integer limit, Integer offset, Long total) {
    }

    public record ListReviewsResponse(List<ReviewResponse> items, PaginationMeta meta) {
    }

    public record ListAnswersResponse(List<AnswerResponse> items, PaginationMeta meta) {
    }

    public record CreateReviewRequest(
        @NotNull Long tmdbMovieId,
        MediaKind mediaType,
        @Size(max = MAX_MOVIE_TITLE_LENGTH) String movieTitle,
        LocalDate movieReleaseDate,
        @Size(max = MAX_POSTER_PATH_LENGTH) String moviePosterPath,
        @NotNull @Min(1) @Max(10) Integer rating,
        @NotBlank @Size(max = MAX_REVIEW_TEXT_LENGTH) String reviewText
    ) {
    }

    public record UpdateReviewRequest(
        @Pattern(regexp = ".*\\S.*", message = MUST_NOT_BE_BLANK) @Size(max = MAX_MOVIE_TITLE_LENGTH) String movieTitle,
        LocalDate movieReleaseDate,
        @Size(max = MAX_POSTER_PATH_LENGTH) String moviePosterPath,
        @Min(1) @Max(10) Integer rating,
        @Pattern(regexp = ".*\\S.*", message = MUST_NOT_BE_BLANK) @Size(max = MAX_REVIEW_TEXT_LENGTH) String reviewText
    ) {
    }

    public record CreateAnswerRequest(@NotBlank @Size(max = MAX_ANSWER_TEXT_LENGTH) String answerText) {
    }

    public record UpdateAnswerRequest(@NotBlank @Size(max = MAX_ANSWER_TEXT_LENGTH) String answerText) {
    }

    public record ErrorDetail(String field, String issue) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorPayload(String code, String message, List<ErrorDetail> details) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(ErrorPayload error) {
    }

    public record MediaResponse(
        UUID mediaId,
        MediaKind mediaType,
        Long tmdbId,
        String title,
        String originalTitle,
        String overview,
        String originalOverview,
        Integer releaseYear,
        LocalDate releaseDate,
        Integer runtimeMinutes,
        String posterPath,
        String posterUrl,
        String director,
        List<String> cast,
        List<MediaCredit> castDetails,
        List<MediaCredit> crewDetails,
        List<String> genres,
        List<String> countries,
        OffsetDateTime lastSyncedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {
    }

    public record MediaSearchItemResponse(
        MediaKind mediaType,
        Long tmdbId,
        String title,
        String originalTitle,
        String overview,
        Integer releaseYear,
        LocalDate releaseDate,
        String posterPath,
        String posterUrl,
        List<String> genres,
        Double rating
    ) {
    }

    public record MediaSearchResponse(List<MediaSearchItemResponse> items, Integer page, Integer totalPages, Long totalResults) {
    }

    public static UserResponse toUserResponse(UserEntity user) {
        return new UserResponse(
            user.getUserId(),
            user.getEmail(),
            user.getUsername(),
            user.getAvatarUrl(),
            user.getBio(),
            user.getRole().name(),
            user.getIsActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    public static PublicUserResponse toPublicUserResponse(UserEntity user) {
        return new PublicUserResponse(user.getUserId(), user.getUsername(), user.getAvatarUrl(), user.getBio());
    }

    public static UserRatingResponse toUserRatingResponse(UserRatingEntity userRating) {
        MediaEntity media = userRating.getMedia();
        return new UserRatingResponse(
            userRating.getRatingId(),
            userRating.getUser().getUserId(),
            media == null ? null : media.getMediaId(),
            userRating.getMediaType(),
            userRating.getTmdbId(),
            media == null ? userRating.getTitle() : media.getTitle(),
            media == null ? userRating.getPosterPath() : media.getPosterPath(),
            media == null ? null : media.getPosterUrl(),
            userRating.getRating(),
            userRating.getSchemeVersion(),
            userRating.getFinalScore(),
            userRating.getCreatedAt(),
            userRating.getUpdatedAt()
        );
    }

    public static ReviewResponse toReviewResponse(ReviewEntity review) {
        return new ReviewResponse(
            review.getReviewId(),
            review.getUser().getUserId(),
            review.getMedia() == null ? null : review.getMedia().getMediaId(),
            review.getMediaType(),
            review.getTmdbMovieId(),
            review.getMovieTitle(),
            review.getMovieReleaseDate(),
            review.getMoviePosterPath(),
            review.getRating(),
            review.getReviewText(),
            review.getUserRating() == null ? null : review.getUserRating().getRatingId(),
            review.getUserRating() == null ? null : review.getUserRating().getSchemeVersion(),
            review.getUserRating() == null ? null : review.getUserRating().getFinalScore(),
            review.getTitle(),
            review.getStatus().name(),
            review.getPublishedAt(),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }

    public static MediaResponse toMediaResponse(MediaEntity media) {
        return new MediaResponse(
            media.getMediaId(),
            media.getMediaType(),
            media.getTmdbId(),
            media.getTitle(),
            media.getOriginalTitle(),
            media.getOverview(),
            media.getOriginalOverview(),
            media.getReleaseYear(),
            media.getReleaseDate(),
            media.getRuntimeMinutes(),
            media.getPosterPath(),
            media.getPosterUrl(),
            media.getDirector(),
            media.getCastMembers(),
            media.getCastDetails(),
            media.getCrewDetails(),
            media.getGenres(),
            media.getCountries(),
            media.getLastSyncedAt(),
            media.getCreatedAt(),
            media.getUpdatedAt()
        );
    }

    public static MediaResponse toMediaResponse(MediaSyncData media) {
        return new MediaResponse(
            null,
            media.mediaType(),
            media.tmdbId(),
            media.title(),
            media.originalTitle(),
            media.overview(),
            media.originalOverview(),
            media.releaseYear(),
            media.releaseDate(),
            media.runtimeMinutes(),
            media.posterPath(),
            media.posterUrl(),
            media.director(),
            media.castMembers(),
            media.castDetails(),
            media.crewDetails(),
            media.genres(),
            media.countries(),
            null,
            null,
            null
        );
    }

    public static AnswerResponse toAnswerResponse(AnswerEntity answer) {
        return new AnswerResponse(
            answer.getAnswerId(),
            answer.getReview().getReviewId(),
            answer.getUser().getUserId(),
            answer.getAnswerText(),
            answer.getAuthorLiked(),
            answer.getCreatedAt(),
            answer.getUpdatedAt()
        );
    }
}
