package org.example.echo.service;

import org.example.echo.entity.AnswerEntity;
import org.example.echo.entity.ReviewEntity;
import org.example.echo.entity.UserEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.model.ApiModels;
import org.example.echo.repository.AnswerRepository;
import org.example.echo.repository.ReviewRepository;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.util.StringNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AnswerService {
    private final AnswerRepository answerRepository;
    private final ReviewRepository reviewRepository;
    private final AuthService authService;

    public AnswerService(
        AnswerRepository answerRepository,
        ReviewRepository reviewRepository,
        AuthService authService
    ) {
        this.answerRepository = answerRepository;
        this.reviewRepository = reviewRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public ApiModels.ListAnswersResponse listAnswers(UUID reviewId, Integer limit, Integer offset) {
        requireReview(reviewId);

        List<AnswerEntity> answers = answerRepository.findByReviewReviewId(reviewId, limit, offset);
        long total = answerRepository.countByReviewReviewId(reviewId);

        return new ApiModels.ListAnswersResponse(
            answers.stream().map(ApiModels::toAnswerResponse).toList(),
            new ApiModels.PaginationMeta(limit, offset, total)
        );
    }

    @Transactional
    public ApiModels.AnswerResponse createAnswer(
        AuthenticatedUserPrincipal actor,
        UUID reviewId,
        ApiModels.CreateAnswerRequest request
    ) {
        UserEntity currentUser = authService.requireUser(actor.userId());
        ReviewEntity review = findReview(reviewId);

        AnswerEntity answer = new AnswerEntity();
        answer.setReview(review);
        answer.setUser(currentUser);
        answer.setAnswerText(StringNormalizer.trim(request.answerText()));
        answerRepository.saveAndFlush(answer);

        return ApiModels.toAnswerResponse(answer);
    }

    @Transactional
    public ApiModels.AnswerResponse updateAnswer(
        AuthenticatedUserPrincipal actor,
        UUID answerId,
        ApiModels.UpdateAnswerRequest request
    ) {
        AnswerEntity answer = findAnswer(answerId);

        authService.checkOwnership(actor, answer.getUser().getUserId());
        answer.setAnswerText(StringNormalizer.trim(request.answerText()));
        answerRepository.saveAndFlush(answer);

        return ApiModels.toAnswerResponse(answer);
    }

    @Transactional
    public void deleteAnswer(AuthenticatedUserPrincipal actor, UUID answerId) {
        AnswerEntity answer = findAnswer(answerId);

        authService.checkOwnership(actor, answer.getUser().getUserId());
        answerRepository.delete(answer);
    }

    private void requireReview(UUID reviewId) {
        findReview(reviewId);
    }

    private ReviewEntity findReview(UUID reviewId) {
        ReviewEntity review = reviewRepository.findByReviewId(reviewId).orElseThrow(this::reviewNotFound);
        if (review.getStatus() != org.example.echo.entity.ReviewStatus.PUBLISHED) throw reviewNotFound();
        return review;
    }

    private AnswerEntity findAnswer(UUID answerId) {
        AnswerEntity answer = answerRepository.findByAnswerId(answerId).orElseThrow(this::answerNotFound);
        if (answer.getReview().getStatus() != org.example.echo.entity.ReviewStatus.PUBLISHED) throw answerNotFound();
        return answer;
    }

    private ApiException reviewNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    private ApiException answerNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }
}
