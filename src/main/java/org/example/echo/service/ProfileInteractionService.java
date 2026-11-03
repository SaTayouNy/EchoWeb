package org.example.echo.service;

import org.example.echo.entity.AnswerEntity;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.ReviewLikeEntity;
import org.example.echo.entity.UserEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.repository.AnswerRepository;
import org.example.echo.repository.ReviewLikeRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileInteractionService {
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewRepository reviewRepository;
    private final AnswerRepository answerRepository;
    private final AuthService authService;
    private final UserRepository userRepository;

    public ProfileInteractionService(
        ReviewLikeRepository reviewLikeRepository,
        ReviewRepository reviewRepository,
        AnswerRepository answerRepository,
        AuthService authService,
        UserRepository userRepository
    ) {
        this.reviewLikeRepository = reviewLikeRepository;
        this.reviewRepository = reviewRepository;
        this.answerRepository = answerRepository;
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @Transactional
    public void likeReview(AuthenticatedUserPrincipal actor, UUID reviewId) {
        UserEntity user = userRepository.findByIdForUpdate(actor.userId()).orElseThrow(this::notFound);
        ReviewEntity review = findReview(reviewId);
        if (review.getUser().getUserId().equals(user.getUserId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_LIKE_NOT_ALLOWED", "A user cannot like their own review");
        }
        if (reviewLikeRepository.existsByReviewReviewIdAndUserUserId(reviewId, user.getUserId())) {
            return;
        }
        ReviewLikeEntity like = new ReviewLikeEntity();
        like.setReview(review);
        like.setUser(user);
        reviewLikeRepository.saveAndFlush(like);
    }

    @Transactional
    public void unlikeReview(AuthenticatedUserPrincipal actor, UUID reviewId) {
        userRepository.findByIdForUpdate(actor.userId()).orElseThrow(this::notFound);
        reviewLikeRepository.findByReviewReviewIdAndUserUserId(reviewId, actor.userId())
            .ifPresent(reviewLikeRepository::delete);
    }

    @Transactional
    public void setAuthorLike(AuthenticatedUserPrincipal actor, UUID answerId, boolean liked) {
        AnswerEntity answer = answerRepository.findByAnswerId(answerId).orElseThrow(this::notFound);
        if (answer.getReview().getStatus() != org.example.echo.entity.ReviewStatus.PUBLISHED) throw notFound();
        authService.checkOwnership(actor, answer.getReview().getUser().getUserId());
        answer.setAuthorLiked(liked);
        answerRepository.saveAndFlush(answer);
    }

    private ReviewEntity findReview(UUID reviewId) {
        ReviewEntity review = reviewRepository.findByReviewId(reviewId).orElseThrow(this::notFound);
        if (review.getStatus() != org.example.echo.entity.ReviewStatus.PUBLISHED) throw notFound();
        return review;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
