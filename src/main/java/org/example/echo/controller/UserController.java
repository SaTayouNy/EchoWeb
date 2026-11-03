package org.example.echo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.echo.entity.MediaKind;
import org.example.echo.model.ApiModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.UserService;
import org.example.echo.service.UserRatingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
public class UserController {
    private final UserService userService;
    private final UserRatingService userRatingService;

    public UserController(UserService userService, UserRatingService userRatingService) {
        this.userService = userService;
        this.userRatingService = userRatingService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiModels.UserResponse me(@AuthenticationPrincipal AuthenticatedUserPrincipal actor) {
        return userService.getCurrentUser(actor);
    }

    @GetMapping("/users/{userId}")
    public ApiModels.PublicUserResponse getUser(@PathVariable UUID userId) {
        return userService.getUser(userId);
    }

    @GetMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiModels.UserProfileResponse currentProfile(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer activityLimit
    ) {
        return userService.getCurrentProfile(actor, activityLimit);
    }

    @PutMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiModels.UserProfileResponse updateProfile(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @Valid @RequestBody ApiModels.UpdateUserProfileRequest request,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer activityLimit
    ) {
        return userService.updateProfile(actor, request, activityLimit);
    }

    @GetMapping("/users/{userId}/profile")
    public ApiModels.UserProfileResponse profile(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer activityLimit
    ) {
        return userService.getProfile(userId, activityLimit);
    }

    @GetMapping("/users/{userId}/ratings")
    public ApiModels.ListUserRatingsResponse ratings(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") @Min(0) Integer page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        return userRatingService.list(userId, page, size);
    }

    @PutMapping("/me/ratings/{mediaType}/{tmdbId}")
    @PreAuthorize("isAuthenticated()")
    public ApiModels.UserRatingResponse upsertRating(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable MediaKind mediaType,
        @PathVariable @Min(1) Long tmdbId,
        @Valid @RequestBody ApiModels.UpsertUserRatingRequest request
    ) {
        return userRatingService.upsert(actor, mediaType, tmdbId, request);
    }

    @DeleteMapping("/me/ratings/{mediaType}/{tmdbId}")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRating(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable MediaKind mediaType,
        @PathVariable @Min(1) Long tmdbId
    ) {
        userRatingService.delete(actor, mediaType, tmdbId);
    }
}
