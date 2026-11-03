package org.example.echo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.echo.model.ApiModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.AnswerService;
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
@RequestMapping("/v1")
public class AnswerController {
    private final AnswerService answerService;
    private final ProfileInteractionService profileInteractionService;

    public AnswerController(AnswerService answerService, ProfileInteractionService profileInteractionService) {
        this.answerService = answerService;
        this.profileInteractionService = profileInteractionService;
    }

    @GetMapping("/reviews/{reviewId}/answers")
    public ApiModels.ListAnswersResponse listAnswers(
        @PathVariable UUID reviewId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit,
        @RequestParam(defaultValue = "0") @Min(0) Integer offset
    ) {
        return answerService.listAnswers(reviewId, limit, offset);
    }

    @PostMapping("/reviews/{reviewId}/answers")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.AnswerResponse createAnswer(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID reviewId,
        @Valid @RequestBody ApiModels.CreateAnswerRequest request
    ) {
        return answerService.createAnswer(actor, reviewId, request);
    }

    @PatchMapping("/answers/{answerId}")
    @PreAuthorize("isAuthenticated()")
    public ApiModels.AnswerResponse updateAnswer(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID answerId,
        @Valid @RequestBody ApiModels.UpdateAnswerRequest request
    ) {
        return answerService.updateAnswer(actor, answerId, request);
    }

    @DeleteMapping("/answers/{answerId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnswer(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID answerId
    ) {
        answerService.deleteAnswer(actor, answerId);
    }

    @PutMapping("/answers/{answerId}/author-like")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void authorLike(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID answerId
    ) {
        profileInteractionService.setAuthorLike(actor, answerId, true);
    }

    @DeleteMapping("/answers/{answerId}/author-like")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAuthorLike(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable UUID answerId
    ) {
        profileInteractionService.setAuthorLike(actor, answerId, false);
    }
}
