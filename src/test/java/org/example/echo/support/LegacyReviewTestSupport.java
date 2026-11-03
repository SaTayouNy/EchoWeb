package org.example.echo.support;

import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.ReviewStatus;
import org.example.echo.entity.UserEntity;
import org.example.echo.entity.UserRatingEntity;
import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.repository.UserRatingRepository;

/** Historical rows remain readable after the scalar review write API is retired. */
public final class LegacyReviewTestSupport {
    private LegacyReviewTestSupport() { }

    public static ReviewEntity publishedReview(UserEntity user, long tmdbId, String title, String text,
                                              MediaRepository mediaRepository,
                                              UserRatingRepository ratingRepository,
                                              ReviewRepository reviewRepository) {
        MediaEntity media = mediaRepository.findByMediaTypeAndTmdbId(MediaKind.MOVIE, tmdbId)
            .orElseGet(() -> {
                MediaEntity created = new MediaEntity();
                created.setMediaType(MediaKind.MOVIE);
                created.setTmdbId(tmdbId);
                created.setTitle(title);
                return mediaRepository.saveAndFlush(created);
            });
        UserRatingEntity rating = new UserRatingEntity();
        rating.setUser(user);
        rating.setMedia(media);
        rating.setMediaType(MediaKind.MOVIE);
        rating.setTmdbId(tmdbId);
        rating.setTitle(title);
        rating.setRating(8);
        ratingRepository.saveAndFlush(rating);

        ReviewEntity review = new ReviewEntity();
        review.setUser(user);
        review.setMedia(media);
        review.setMediaType(MediaKind.MOVIE);
        review.setTmdbMovieId(tmdbId);
        review.setMovieTitle(title);
        review.setRating(8);
        review.setReviewText(text);
        review.setStatus(ReviewStatus.PUBLISHED);
        review.setUserRating(rating);
        return reviewRepository.saveAndFlush(review);
    }
}
