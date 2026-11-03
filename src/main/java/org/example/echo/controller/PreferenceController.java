package org.example.echo.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.echo.entity.PreferenceType;
import org.example.echo.model.PreferenceModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.UserPreferenceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/me/preferences")
@PreAuthorize("isAuthenticated()")
public class PreferenceController {
    private final UserPreferenceService service;

    public PreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public PreferenceModels.ListPreferencesResponse list(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @RequestParam(required = false) PreferenceType type,
        @RequestParam(defaultValue = "0") @Min(0) Integer page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        return service.list(actor, type, page, size);
    }

    @GetMapping("/{type}/{tmdbId}")
    public PreferenceModels.PreferenceResponse get(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable PreferenceType type,
        @PathVariable @Min(1) Long tmdbId
    ) {
        return service.get(actor, type, tmdbId);
    }

    @PutMapping("/{type}/{tmdbId}")
    public PreferenceModels.PreferenceResponse add(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable PreferenceType type,
        @PathVariable @Min(1) Long tmdbId,
        @Valid @RequestBody(required = false) PreferenceModels.PersonSourceRequest source
    ) {
        return service.add(actor, type, tmdbId, source);
    }

    @DeleteMapping("/{type}/{tmdbId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal AuthenticatedUserPrincipal actor,
        @PathVariable PreferenceType type,
        @PathVariable @Min(1) Long tmdbId
    ) {
        service.delete(actor, type, tmdbId);
    }
}
